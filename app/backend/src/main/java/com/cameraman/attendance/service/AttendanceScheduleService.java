package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceHoliday;
import com.cameraman.attendance.domain.StaffSchedule;
import com.cameraman.attendance.domain.WorkShift;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceHolidayRepository;
import com.cameraman.attendance.repository.StaffScheduleRepository;
import com.cameraman.attendance.repository.WorkShiftRepository;
import com.cameraman.attendance.service.AttendanceCalculator.Rules;
import com.cameraman.attendance.service.AttendanceCalculator.Schedule;
import com.cameraman.audit.service.AuditService;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shifts, per-staff schedules and holidays, and the lookup that turns "this staff member" into the
 * {@link Schedule} the attendance calculator needs. Anything not set per staff falls back to the
 * studio-wide settings, so a studio that never touches shifts behaves exactly as before.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceScheduleService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkShiftRepository shiftRepository;
    private final StaffScheduleRepository scheduleRepository;
    private final AttendanceHolidayRepository holidayRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final StudioSettingsService settingsService;
    private final AuditService auditService;

    // ------------------------------------------------------------------ resolution context

    /** Everything needed to evaluate many staff over a date range with a handful of queries. */
    public static class Context {
        public Schedule defaultSchedule;
        public Rules rules;
        public int latesPerHalfDay;
        public final Map<String, Schedule> byStaff = new HashMap<>();
        public final Map<String, String> shiftNameByStaff = new HashMap<>();
        public final Map<String, StaffSchedule> rawByStaff = new HashMap<>();
        public final Map<LocalDate, String> holidays = new HashMap<>();

        public Schedule scheduleFor(String staffId) {
            return byStaff.getOrDefault(staffId, defaultSchedule);
        }
    }

    @Transactional(readOnly = true)
    public Context loadContext(LocalDate from, LocalDate to) {
        Context ctx = new Context();
        ctx.defaultSchedule = defaultSchedule();
        ctx.rules = new Rules(Math.max(0, settingsService.getAttendanceHalfDayMinutes()),
                Math.max(0, settingsService.getAttendanceMinOvertimeMinutes()));
        ctx.latesPerHalfDay = Math.max(0, settingsService.getAttendanceLatesPerHalfDay());

        Map<String, WorkShift> shifts = shiftRepository.findAll().stream()
                .collect(Collectors.toMap(WorkShift::getId, s -> s));
        for (StaffSchedule ss : scheduleRepository.findAll()) {
            ctx.rawByStaff.put(ss.getStaffId(), ss);
            WorkShift shift = ss.getShiftId() != null ? shifts.get(ss.getShiftId()) : null;
            boolean useShift = shift != null && shift.isActive();
            Set<DayOfWeek> off = parseDays(ss.getWeeklyOffDays());
            ctx.byStaff.put(ss.getStaffId(), new Schedule(
                    useShift ? shift.getStartTime() : ctx.defaultSchedule.start(),
                    useShift ? shift.getEndTime() : ctx.defaultSchedule.end(),
                    useShift ? shift.getGraceMinutes() : ctx.defaultSchedule.graceMinutes(),
                    off.isEmpty() ? ctx.defaultSchedule.weeklyOff() : off));
            if (useShift) ctx.shiftNameByStaff.put(ss.getStaffId(), shift.getName());
        }
        for (AttendanceHoliday h : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)) {
            ctx.holidays.put(h.getHolidayDate(), h.getName());
        }
        return ctx;
    }

    private Schedule defaultSchedule() {
        LocalTime start = parseTime(settingsService.getAttendanceShiftStart(), LocalTime.of(9, 30));
        LocalTime end = parseTime(settingsService.getAttendanceShiftEnd(), LocalTime.of(18, 30));
        if (!end.isAfter(start)) end = LocalTime.of(23, 59);
        Set<DayOfWeek> off = parseDays(settingsService.getAttendanceWeeklyOffDay());
        if (off.isEmpty()) off = Set.of(DayOfWeek.SUNDAY);
        return new Schedule(start, end, Math.max(0, settingsService.getAttendanceGraceMinutes()), off);
    }

    // ------------------------------------------------------------------ shifts

    @Transactional(readOnly = true)
    public List<AttendanceDto.ShiftResponse> listShifts() {
        return shiftRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AttendanceDto.ShiftResponse createShift(AttendanceDto.ShiftRequest req, String actorId) {
        String name = req.getName().trim();
        if (shiftRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Shift", "name", name);
        }
        WorkShift shift = WorkShift.builder().name(name).build();
        applyShift(shift, req);
        shift = shiftRepository.save(shift);
        auditService.log("SHIFT_CREATE", "WorkShift", shift.getId(), null, name, actorId);
        return toResponse(shift);
    }

    public AttendanceDto.ShiftResponse updateShift(String id, AttendanceDto.ShiftRequest req, String actorId) {
        WorkShift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
        String name = req.getName().trim();
        if (!name.equalsIgnoreCase(shift.getName()) && shiftRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Shift", "name", name);
        }
        shift.setName(name);
        applyShift(shift, req);
        shift = shiftRepository.save(shift);
        auditService.log("SHIFT_UPDATE", "WorkShift", id, null, name, actorId);
        return toResponse(shift);
    }

    public void deleteShift(String id, String actorId) {
        WorkShift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
        long users = scheduleRepository.countByShiftId(id);
        if (users > 0) {
            throw new BusinessException(users + " staff member(s) use this shift. Move them to another shift, or mark it inactive instead.", "SHIFT_IN_USE");
        }
        shiftRepository.delete(shift);
        auditService.log("SHIFT_DELETE", "WorkShift", id, shift.getName(), null, actorId);
    }

    private void applyShift(WorkShift shift, AttendanceDto.ShiftRequest req) {
        LocalTime start = requireTime(req.getStartTime(), "start time");
        LocalTime end = requireTime(req.getEndTime(), "end time");
        if (!end.isAfter(start)) {
            throw new BusinessException("The shift must end after it starts (overnight shifts are not supported)", "INVALID_SHIFT_HOURS");
        }
        shift.setStartTime(start);
        shift.setEndTime(end);
        if (req.getGraceMinutes() != null) shift.setGraceMinutes(req.getGraceMinutes());
        if (req.getActive() != null) shift.setActive(req.getActive());
    }

    // ------------------------------------------------------------------ per-staff schedules

    @Transactional(readOnly = true)
    public List<AttendanceDto.ScheduleResponse> listSchedules() {
        Context ctx = loadContext(LocalDate.now(), LocalDate.now());
        List<StaffProfile> staff = staffRepository.findAll();
        Map<String, User> users = userRepository.findAllById(
                staff.stream().map(StaffProfile::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<String, String> shiftNames = shiftRepository.findAll().stream()
                .collect(Collectors.toMap(WorkShift::getId, WorkShift::getName));
        return staff.stream()
                .map(sp -> toScheduleResponse(sp, users.get(sp.getUserId()), ctx, shiftNames))
                .sorted(Comparator.comparing(AttendanceDto.ScheduleResponse::getStaffName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public AttendanceDto.ScheduleResponse updateSchedule(String staffId, AttendanceDto.ScheduleRequest req, String actorId) {
        StaffProfile staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        if (req.getShiftId() != null && !req.getShiftId().isBlank()) {
            WorkShift shift = shiftRepository.findById(req.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", req.getShiftId()));
            if (!shift.isActive()) {
                throw new BusinessException("That shift is inactive", "SHIFT_INACTIVE");
            }
        }
        List<String> days = req.getWeeklyOffDays() == null ? List.of() : req.getWeeklyOffDays();
        for (String d : days) {
            try { DayOfWeek.valueOf(d.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new BusinessException("Unknown day: " + d, "INVALID_DAY"); }
        }

        StaffSchedule ss = scheduleRepository.findByStaffId(staffId)
                .orElse(StaffSchedule.builder().staffId(staffId).build());
        ss.setShiftId(req.getShiftId() == null || req.getShiftId().isBlank() ? null : req.getShiftId());
        ss.setWeeklyOffDays(days.isEmpty() ? null
                : days.stream().map(d -> d.trim().toUpperCase()).distinct().collect(Collectors.joining(",")));
        ss.setMonthlySalary(req.getMonthlySalary());
        ss.setOvertimeRatePerHour(req.getOvertimeRatePerHour());
        scheduleRepository.save(ss);
        auditService.log("SCHEDULE_UPDATE", "StaffSchedule", staffId, null, "shift/off-days/pay updated", actorId);

        Context ctx = loadContext(LocalDate.now(), LocalDate.now());
        User user = userRepository.findById(staff.getUserId()).orElse(null);
        Map<String, String> shiftNames = shiftRepository.findAll().stream()
                .collect(Collectors.toMap(WorkShift::getId, WorkShift::getName));
        return toScheduleResponse(staff, user, ctx, shiftNames);
    }

    // ------------------------------------------------------------------ holidays

    @Transactional(readOnly = true)
    public List<AttendanceDto.HolidayResponse> listHolidays(int year) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(h -> AttendanceDto.HolidayResponse.builder()
                        .id(h.getId()).date(h.getHolidayDate()).name(h.getName()).build())
                .collect(Collectors.toList());
    }

    public AttendanceDto.HolidayResponse addHoliday(AttendanceDto.HolidayRequest req, String actorId) {
        if (holidayRepository.existsByHolidayDate(req.getDate())) {
            throw new DuplicateResourceException("Holiday", "date", req.getDate().toString());
        }
        AttendanceHoliday h = holidayRepository.save(AttendanceHoliday.builder()
                .holidayDate(req.getDate()).name(req.getName().trim()).build());
        auditService.log("HOLIDAY_CREATE", "AttendanceHoliday", h.getId(), null, h.getName(), actorId);
        return AttendanceDto.HolidayResponse.builder().id(h.getId()).date(h.getHolidayDate()).name(h.getName()).build();
    }

    public void deleteHoliday(String id, String actorId) {
        AttendanceHoliday h = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", "id", id));
        holidayRepository.delete(h);
        auditService.log("HOLIDAY_DELETE", "AttendanceHoliday", id, h.getName(), null, actorId);
    }

    // ------------------------------------------------------------------ helpers

    private AttendanceDto.ShiftResponse toResponse(WorkShift s) {
        return AttendanceDto.ShiftResponse.builder()
                .id(s.getId()).name(s.getName())
                .startTime(s.getStartTime().format(HHMM)).endTime(s.getEndTime().format(HHMM))
                .graceMinutes(s.getGraceMinutes()).active(s.isActive())
                .staffCount(scheduleRepository.countByShiftId(s.getId()))
                .build();
    }

    private AttendanceDto.ScheduleResponse toScheduleResponse(StaffProfile sp, User user, Context ctx, Map<String, String> shiftNames) {
        StaffSchedule raw = ctx.rawByStaff.get(sp.getId());
        Schedule effective = ctx.scheduleFor(sp.getId());
        String name = user == null ? null
                : (user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
        List<String> off = effective.weeklyOff().stream().map(DayOfWeek::name).sorted().collect(Collectors.toList());
        List<String> ownOff = raw == null ? List.of() : new ArrayList<>(parseDays(raw.getWeeklyOffDays()).stream().map(DayOfWeek::name).sorted().toList());
        return AttendanceDto.ScheduleResponse.builder()
                .staffId(sp.getId()).staffName(name).employeeId(sp.getEmployeeId()).department(sp.getDepartment())
                .shiftId(raw != null ? raw.getShiftId() : null)
                .shiftName(raw != null && raw.getShiftId() != null ? shiftNames.get(raw.getShiftId()) : null)
                .effectiveStart(effective.start().format(HHMM)).effectiveEnd(effective.end().format(HHMM))
                .weeklyOffDays(ownOff.isEmpty() ? off : ownOff)
                .usesDefaultShift(!ctx.shiftNameByStaff.containsKey(sp.getId()))
                .monthlySalary(raw != null ? raw.getMonthlySalary() : null)
                .overtimeRatePerHour(raw != null ? raw.getOvertimeRatePerHour() : null)
                .build();
    }

    private static LocalTime requireTime(String raw, String label) {
        try { return LocalTime.parse(raw.trim()); }
        catch (Exception e) { throw new BusinessException("Enter the " + label + " as HH:mm", "INVALID_TIME"); }
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        try { return LocalTime.parse(raw.trim()); } catch (Exception e) { return fallback; }
    }

    static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            try { out.add(DayOfWeek.valueOf(part.trim().toUpperCase())); } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }
}
