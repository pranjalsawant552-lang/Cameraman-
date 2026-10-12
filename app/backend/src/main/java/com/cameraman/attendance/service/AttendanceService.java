package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.AttendanceRecord;
import com.cameraman.attendance.domain.AttendanceSource;
import com.cameraman.attendance.domain.LeaveRequest;
import com.cameraman.attendance.domain.PunchDirection;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.attendance.repository.AttendanceRecordRepository;
import com.cameraman.attendance.repository.LeaveRequestRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vendor-neutral attendance ingestion.
 *
 * Every path below (manual entry, CSV import, generic webhook, ZKTeco/ADMS protocol)
 * funnels into {@link #recordPunch}, which is the one place that knows how to resolve
 * a device's raw employee code to a staff member. Adding support for a genuinely new
 * protocol later means writing a small parser that calls recordPunch — the resolution,
 * storage, and reporting logic is never duplicated per device type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveRequestRepository leaveRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final StudioSettingsService settingsService;
    private final AttendanceDeviceService deviceService;
    private final AttendanceScheduleService scheduleService;

    @Value("${app.api-url:http://localhost:8080/api}")
    private String apiBaseUrl;

    /** Comma-separated serial numbers (SN) of attendance machines allowed to push without the
     *  shared key. ADMS/iClock machines only let you type a server address + port, so they
     *  cannot append ?key=... — they identify themselves by serial number instead. Use "*" to
     *  accept any serial (only on a trusted LAN). Blank = key required (default). */
    @Value("${attendance.allowed-device-serials:}")
    private String allowedDeviceSerials;

    private static final DateTimeFormatter[] TIMESTAMP_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
    };

    // ─────────────────────────────── Core ───────────────────────────────

    /**
     * Records a punch. Idempotent: if the same code + time + device was already stored, nothing is
     * written and the existing row is returned (devices re-send logs after a reconnect, and a
     * CSV may overlap an earlier import).
     */
    public AttendanceRecord recordPunch(String employeeCode, LocalDateTime punchTime, PunchDirection direction,
                                         AttendanceSource source, String deviceSerial, String rawPayload) {
        return recordPunchIfNew(employeeCode, punchTime, direction, source, deviceSerial, rawPayload).orElse(null);
    }

    /** @return the saved record, or empty when this exact punch already existed (a duplicate). */
    public Optional<AttendanceRecord> recordPunchIfNew(String employeeCode, LocalDateTime punchTime, PunchDirection direction,
                                         AttendanceSource source, String deviceSerial, String rawPayload) {
        if (employeeCode == null || employeeCode.isBlank()) {
            throw new BusinessException("An employee code is required for every punch", "MISSING_EMPLOYEE_CODE");
        }
        String code = employeeCode.trim();
        String serial = deviceSerial != null && !deviceSerial.isBlank() ? deviceSerial.trim() : null;
        if (attendanceRepository.existsPunch(code, punchTime, serial)) {
            return Optional.empty();
        }
        StaffProfile staff = staffRepository.findByEmployeeId(code).orElse(null);
        String staffName = null;
        if (staff != null) {
            User user = userRepository.findById(staff.getUserId()).orElse(null);
            if (user != null) { staffName = user.getFirstName() + " " + user.getLastName(); }
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .employeeCode(code)
                .staffId(staff != null ? staff.getId() : null)
                .staffName(staffName)
                .punchTime(punchTime)
                .direction(direction != null ? direction : PunchDirection.UNKNOWN)
                .source(source)
                .deviceSerial(serial)
                .rawPayload(rawPayload)
                .build();

        try {
            return Optional.of(attendanceRepository.saveAndFlush(record));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return Optional.empty(); // lost a race with an identical punch: the unique index kept one
        }
    }

    /** Auth for device-facing requests: valid shared key, OR a whitelisted device serial. */
    public void verifyDeviceRequest(String deviceSerial, String suppliedKey) {
        if (suppliedKey != null && !suppliedKey.isBlank()) {
            verifyWebhookKey(suppliedKey);
            if (deviceSerial != null && !deviceSerial.isBlank()) deviceService.noteSeen(deviceSerial);
            return;
        }
        if (deviceSerial != null && !deviceSerial.isBlank()) {
            // The environment list is only a bootstrap/override; the database list is what admins manage.
            boolean allowedByEnv = false;
            if (allowedDeviceSerials != null && !allowedDeviceSerials.isBlank()) {
                for (String allowed : allowedDeviceSerials.split(",")) {
                    String a = allowed.trim();
                    if (a.equals("*") || a.equalsIgnoreCase(deviceSerial.trim())) { allowedByEnv = true; break; }
                }
            }
            boolean allowedByDb = deviceService.noteSeen(deviceSerial); // also records first-time devices
            if (allowedByEnv || allowedByDb) { return; }
        }
        throw new BusinessException("Invalid or missing attendance webhook key", "ATTENDANCE_AUTH_FAILED");
    }

    public void verifyWebhookKey(String suppliedKey) {
        String expected = settingsService.getOrCreateAttendanceWebhookSecret();
        if (suppliedKey == null || !expected.equals(suppliedKey.trim())) {
            throw new BusinessException("Invalid or missing attendance webhook key", "ATTENDANCE_AUTH_FAILED");
        }
    }

    // ─────────────────────────────── Manual entry ───────────────────────────────

    public AttendanceDto.RecordResponse manualEntry(AttendanceDto.ManualEntryRequest request) {
        StaffProfile staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        PunchDirection direction = parseDirection(request.getDirection());
        User user = userRepository.findById(staff.getUserId()).orElse(null);

        AttendanceRecord record = AttendanceRecord.builder()
                .employeeCode(staff.getEmployeeId() != null ? staff.getEmployeeId() : staff.getId())
                .staffId(staff.getId())
                .staffName(user != null ? user.getFirstName() + " " + user.getLastName() : null)
                .punchTime(request.getPunchTime())
                .direction(direction)
                .source(AttendanceSource.MANUAL)
                .notes(request.getNotes())
                .build();

        return toResponse(attendanceRepository.save(record));
    }

    // ─────────────────────────────── Generic webhook ───────────────────────────────

    public void ingestGenericWebhook(AttendanceDto.GenericWebhookRequest request) {
        verifyWebhookKey(request.getKey());
        recordPunch(request.getEmployeeCode(), request.getTimestamp(), parseDirection(request.getDirection()),
                AttendanceSource.GENERIC_WEBHOOK, request.getDeviceSerial(), null);
    }

    // ─────────────────────────────── ZKTeco / ADMS protocol ───────────────────────────────

    /**
     * Parses the iClock attendance-log upload format used by the ZKTeco push
     * protocol (and the many OEM/rebadged devices built on the same firmware).
     * Each line is tab-separated: PIN, time, status, verify-mode, work-code...
     * Only PIN (employee code) and time are required; extra columns are ignored.
     */
    public int ingestZkAttendanceLog(String deviceSerial, String body) {
        if (body == null || body.isBlank()) { return 0; }
        int count = 0, duplicates = 0;
        for (String line : body.split("\\r?\\n")) {
            if (line.isBlank()) { continue; }
            String[] parts = line.split("\\t");
            if (parts.length < 2) { continue; }
            String pin = parts[0].trim();
            LocalDateTime time = parseTimestampLenient(parts[1].trim());
            if (time == null) { continue; }
            PunchDirection direction = parts.length > 2 ? zkStatusToDirection(parts[2].trim()) : PunchDirection.UNKNOWN;
            try {
                if (recordPunchIfNew(pin, time, direction, AttendanceSource.ZK_PROTOCOL, deviceSerial, line).isPresent()) {
                    count++;
                } else {
                    duplicates++;
                }
            } catch (Exception e) {
                log.warn("Skipped unparseable ZK attendance line from device {}: {}", deviceSerial, line);
            }
        }
        deviceService.addPunches(deviceSerial, count);
        if (duplicates > 0) log.info("Device {} re-sent {} punches that were already stored", deviceSerial, duplicates);
        return count;
    }

    /**
     * ZK "status" column: 0 = check-in, 4 = overtime-in -> IN; 1 = check-out, 5 = overtime-out -> OUT.
     * Anything else (2/3 break, blank, vendor codes) is UNKNOWN. Many machines send the same status
     * for every punch, so daily in/out never relies on this: DaySummary/MonthlyReport use the first
     * and last punch of the day, and this value is stored only as extra information.
     */
    static PunchDirection zkStatusToDirection(String status) {
        return switch (status == null ? "" : status.trim()) {
            case "0", "4" -> PunchDirection.IN;
            case "1", "5" -> PunchDirection.OUT;
            default -> PunchDirection.UNKNOWN;
        };
    }

    // ─────────────────────────────── CSV import ───────────────────────────────

    /**
     * Expects a CSV with header: Employee Code, Timestamp, Direction (Direction optional).
     * This is the universal fallback — whatever proprietary software shipped with the
     * device, if it can export attendance logs to Excel/CSV (nearly all can), the studio
     * can bulk-import here without any live integration at all.
     */
    public AttendanceDto.ImportResult importCsv(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int total = 0, imported = 0, duplicates = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header row
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) { continue; }
                total++;
                String[] cols = splitCsvLine(line);
                if (cols.length < 2) {
                    errors.add("Row " + rowNum + ": expected at least Employee Code, Timestamp columns");
                    continue;
                }
                String employeeCode = cols[0].trim();
                LocalDateTime timestamp = parseTimestampLenient(cols[1].trim());
                if (employeeCode.isEmpty() || timestamp == null) {
                    errors.add("Row " + rowNum + ": couldn't parse employee code or timestamp");
                    continue;
                }
                PunchDirection direction = cols.length > 2 ? parseDirection(cols[2].trim()) : PunchDirection.UNKNOWN;
                try {
                    if (recordPunchIfNew(employeeCode, timestamp, direction, AttendanceSource.CSV_IMPORT, null, line).isPresent()) {
                        imported++;
                    } else {
                        duplicates++;
                    }
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Couldn't read that file: " + e.getMessage(), "CSV_READ_ERROR");
        }

        return AttendanceDto.ImportResult.builder()
                .totalRows(total).imported(imported).duplicates(duplicates).skipped(total - imported - duplicates)
                .errors(errors.size() > 20 ? errors.subList(0, 20) : errors)
                .build();
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    // ─────────────────────────────── Linking & queries ───────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceDto.UnmatchedCode> getUnmatchedCodes() {
        // Grouped in SQL: the old version loaded every punch ever recorded just to find the strays.
        return attendanceRepository.findUnmatchedSummary().stream()
                .map(r -> AttendanceDto.UnmatchedCode.builder()
                        .employeeCode(r.getEmployeeCode())
                        .punchCount(r.getPunchCount())
                        .lastSeen(r.getLastSeen())
                        .build())
                .sorted(Comparator.comparing(AttendanceDto.UnmatchedCode::getLastSeen, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public void linkCode(AttendanceDto.LinkCodeRequest request) {
        StaffProfile staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        User user = userRepository.findById(staff.getUserId()).orElse(null);
        String staffName = user != null ? user.getFirstName() + " " + user.getLastName() : null;
        String code = request.getEmployeeCode().trim();

        // If another staff member already owns this code, linking would silently steal their punches.
        staffRepository.findByEmployeeId(code).ifPresent(other -> {
            if (!other.getId().equals(staff.getId())) {
                throw new BusinessException("That code already belongs to another staff member", "CODE_ALREADY_ASSIGNED");
            }
        });

        // One UPDATE statement backfills every unmatched punch with this raw code.
        attendanceRepository.linkCodeToStaff(code, staff.getId(), staffName);

        if (staff.getEmployeeId() == null || staff.getEmployeeId().isBlank()) {
            staff.setEmployeeId(code);
            staffRepository.save(staff);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<AttendanceDto.RecordResponse> search(String staffId, LocalDate from, LocalDate to, Pageable pageable) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
        Page<AttendanceRecord> page = attendanceRepository.search(staffId, fromDt, toDt, pageable);
        return PagedResponse.<AttendanceDto.RecordResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.DaySummary> daySummary(LocalDate date) {
        // Lists every staff member (an absent person is exactly who this screen must surface), using
        // each person's own shift and weekly off, studio holidays, and the late / half-day / overtime rules.
        List<AttendanceRecord> records = attendanceRepository.findByPunchTimeBetweenOrderByPunchTimeAsc(
                date.atStartOfDay(), date.atTime(23, 59, 59));
        Map<String, List<LocalDateTime>> punchesByStaff = new HashMap<>();
        for (AttendanceRecord r : records) {
            if (r.getStaffId() != null) {
                punchesByStaff.computeIfAbsent(r.getStaffId(), k -> new ArrayList<>()).add(r.getPunchTime());
            }
        }

        AttendanceScheduleService.Context ctx = scheduleService.loadContext(date, date);
        LocalDate today = LocalDate.now();
        List<LeaveRequest> approvedLeaves = leaveRepository.findApprovedOverlappingAnyStaff(date, date);

        List<StaffProfile> allStaff = staffRepository.findAll();
        Map<String, User> usersById = userRepository.findAllById(
                allStaff.stream().map(StaffProfile::getUserId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        return allStaff.stream().map(staff -> {
            boolean onLeave = approvedLeaves.stream().anyMatch(l -> staff.getId().equals(l.getStaffId()) && l.covers(date));
            AttendanceCalculator.DayResult r = AttendanceCalculator.evaluate(date, today, ctx.scheduleFor(staff.getId()),
                    ctx.rules, ctx.holidays.containsKey(date), onLeave, punchesByStaff.getOrDefault(staff.getId(), List.of()));
            User user = usersById.get(staff.getUserId());
            return AttendanceDto.DaySummary.builder()
                    .staffId(staff.getId())
                    .staffName(displayName(user))
                    .firstPunch(r.firstPunch())
                    .lastPunch(r.lastPunch())
                    .punchCount(r.punchCount())
                    .workedMinutes(r.workedMinutes())
                    .lateMinutes(r.lateMinutes())
                    .overtimeMinutes(r.overtimeMinutes())
                    .status(r.status().name())
                    .build();
        }).sorted(Comparator.comparing(AttendanceDto.DaySummary::getStaffName, Comparator.nullsLast(Comparator.naturalOrder())))
          .collect(Collectors.toList());
    }

    // ─────────────────────────────── Monthly report ───────────────────────────────

    @Transactional(readOnly = true)
    public AttendanceDto.MonthlyReport monthlyReport(String staffId, int year, int month) {
        StaffProfile staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        return monthlyReports(List.of(staff), year, month).get(0);
    }

    /** One report per staff member for the month, loading punches, leave and holidays once for everyone. */
    @Transactional(readOnly = true)
    public List<AttendanceDto.MonthlyReport> monthlyReportsForAll(int year, int month) {
        return monthlyReports(staffRepository.findAll(), year, month);
    }

    private List<AttendanceDto.MonthlyReport> monthlyReports(List<StaffProfile> staffList, int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BusinessException("Choose a valid year and month", "INVALID_PERIOD");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        AttendanceScheduleService.Context ctx = scheduleService.loadContext(monthStart, monthEnd);

        Set<String> wanted = staffList.stream().map(StaffProfile::getId).collect(Collectors.toSet());
        Map<String, Map<LocalDate, List<LocalDateTime>>> punches = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByPunchTimeBetweenOrderByPunchTimeAsc(
                monthStart.atStartOfDay(), monthEnd.atTime(23, 59, 59))) {
            if (r.getStaffId() != null && wanted.contains(r.getStaffId())) {
                punches.computeIfAbsent(r.getStaffId(), k -> new HashMap<>())
                        .computeIfAbsent(r.getPunchTime().toLocalDate(), k -> new ArrayList<>()).add(r.getPunchTime());
            }
        }
        Map<String, List<LeaveRequest>> leaves = new HashMap<>();
        for (LeaveRequest l : leaveRepository.findApprovedOverlappingAnyStaff(monthStart, monthEnd)) {
            if (wanted.contains(l.getStaffId())) {
                leaves.computeIfAbsent(l.getStaffId(), k -> new ArrayList<>()).add(l);
            }
        }
        Map<String, User> users = userRepository.findAllById(
                staffList.stream().map(StaffProfile::getUserId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<AttendanceDto.MonthlyReport> out = new ArrayList<>();
        for (StaffProfile staff : staffList) {
            AttendanceCalculator.Schedule schedule = ctx.scheduleFor(staff.getId());
            Map<LocalDate, List<LocalDateTime>> staffPunches = punches.getOrDefault(staff.getId(), Map.of());
            List<LeaveRequest> staffLeaves = leaves.getOrDefault(staff.getId(), List.of());

            List<AttendanceDto.DayStatus> days = new ArrayList<>();
            int present = 0, late = 0, absent = 0, onLeaveDays = 0, weeklyOffCount = 0, half = 0, holidays = 0;
            long worked = 0, lateMin = 0, overtime = 0;

            for (LocalDate day = monthStart; !day.isAfter(monthEnd); day = day.plusDays(1)) {
                final LocalDate d = day;
                AttendanceCalculator.DayResult r = AttendanceCalculator.evaluate(d, today, schedule, ctx.rules,
                        ctx.holidays.containsKey(d), staffLeaves.stream().anyMatch(l -> l.covers(d)),
                        staffPunches.getOrDefault(d, List.of()));
                switch (r.status()) {
                    case PRESENT -> present++;
                    case LATE -> late++;
                    case HALF_DAY -> half++;
                    case ABSENT -> absent++;
                    case ON_LEAVE -> onLeaveDays++;
                    case WEEKLY_OFF -> weeklyOffCount++;
                    case HOLIDAY -> holidays++;
                    default -> { }
                }
                worked += r.workedMinutes();
                lateMin += r.lateMinutes();
                overtime += r.overtimeMinutes();
                days.add(AttendanceDto.DayStatus.builder()
                        .date(d).status(r.status().name())
                        .firstPunch(r.firstPunch()).lastPunch(r.lastPunch())
                        .punchCount(r.punchCount())
                        .workedMinutes(r.workedMinutes()).lateMinutes(r.lateMinutes())
                        .earlyLeaveMinutes(r.earlyLeaveMinutes()).overtimeMinutes(r.overtimeMinutes())
                        .missingOut(r.missingOut())
                        .holidayName(ctx.holidays.get(d))
                        .build());
            }

            int workingDays = 0;
            for (LocalDate day = monthStart; !day.isAfter(monthEnd); day = day.plusDays(1)) {
                if (!schedule.weeklyOff().contains(day.getDayOfWeek()) && !ctx.holidays.containsKey(day)) workingDays++;
            }

            out.add(AttendanceDto.MonthlyReport.builder()
                    .staffId(staff.getId()).staffName(displayName(users.get(staff.getUserId())))
                    .year(year).month(month).days(days)
                    .presentDays(present).lateDays(late).absentDays(absent)
                    .leaveDays(onLeaveDays).weeklyOffDays(weeklyOffCount)
                    .halfDays(half).holidayDays(holidays).workingDays(workingDays)
                    .workedMinutes(worked).lateMinutes(lateMin).overtimeMinutes(overtime)
                    .shiftName(ctx.shiftNameByStaff.get(staff.getId()))
                    .build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public AttendanceDto.WorkHoursConfig getWorkHoursConfig() {
        AttendanceDto.WorkHoursConfig config = new AttendanceDto.WorkHoursConfig();
        config.setShiftStart(settingsService.getAttendanceShiftStart());
        config.setGraceMinutes(settingsService.getAttendanceGraceMinutes());
        config.setWeeklyOffDay(settingsService.getAttendanceWeeklyOffDay());
        config.setShiftEnd(settingsService.getAttendanceShiftEnd());
        config.setHalfDayMinutes(settingsService.getAttendanceHalfDayMinutes());
        config.setMinOvertimeMinutes(settingsService.getAttendanceMinOvertimeMinutes());
        config.setLatesPerHalfDay(settingsService.getAttendanceLatesPerHalfDay());
        return config;
    }

    public AttendanceDto.WorkHoursConfig updateWorkHoursConfig(AttendanceDto.WorkHoursConfig config) {
        validateTime(config.getShiftStart(), "shift start");
        validateTime(config.getShiftEnd(), "shift end");
        if (config.getShiftStart() != null && !config.getShiftStart().isBlank()
                && config.getShiftEnd() != null && !config.getShiftEnd().isBlank()
                && !LocalTime.parse(config.getShiftEnd().trim()).isAfter(LocalTime.parse(config.getShiftStart().trim()))) {
            throw new BusinessException("The shift must end after it starts", "INVALID_SHIFT_HOURS");
        }
        for (Integer v : new Integer[]{config.getHalfDayMinutes(), config.getMinOvertimeMinutes(), config.getLatesPerHalfDay()}) {
            if (v != null && (v < 0 || v > 1440)) {
                throw new BusinessException("Minutes and counts must be between 0 and 1440", "INVALID_RULE");
            }
        }
        settingsService.updateAttendanceWorkHours(config.getShiftStart(), config.getGraceMinutes(), config.getWeeklyOffDay());
        settingsService.updateAttendanceRules(config.getShiftEnd(), config.getHalfDayMinutes(),
                config.getMinOvertimeMinutes(), config.getLatesPerHalfDay());
        return getWorkHoursConfig();
    }

    private static void validateTime(String raw, String label) {
        if (raw == null || raw.isBlank()) return;
        try { LocalTime.parse(raw.trim()); }
        catch (Exception e) { throw new BusinessException("Enter the " + label + " as HH:mm", "INVALID_TIME"); }
    }

    private static String displayName(User user) {
        return user == null ? null
                : (user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }

    @Transactional(readOnly = true)
    public AttendanceDto.WebhookConfig getWebhookConfig() {
        String secret = settingsService.getOrCreateAttendanceWebhookSecret();
        return AttendanceDto.WebhookConfig.builder()
                .secret(secret)
                .genericWebhookUrl(apiBaseUrl + "/webhooks/attendance/generic")
                .iclockBaseUrl(apiBaseUrl + "/webhooks/attendance/iclock?key=" + secret)
                .build();
    }

    public AttendanceDto.WebhookConfig regenerateSecret() {
        String secret = settingsService.regenerateAttendanceWebhookSecret();
        return AttendanceDto.WebhookConfig.builder()
                .secret(secret)
                .genericWebhookUrl(apiBaseUrl + "/webhooks/attendance/generic")
                .iclockBaseUrl(apiBaseUrl + "/webhooks/attendance/iclock?key=" + secret)
                .build();
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private PunchDirection parseDirection(String raw) {
        if (raw == null || raw.isBlank()) { return PunchDirection.UNKNOWN; }
        String v = raw.trim().toUpperCase();
        if (v.startsWith("IN") || v.equals("0") || v.equals("CHECK-IN")) { return PunchDirection.IN; }
        if (v.startsWith("OUT") || v.equals("1") || v.equals("CHECK-OUT")) { return PunchDirection.OUT; }
        try { return PunchDirection.valueOf(v); } catch (Exception e) { return PunchDirection.UNKNOWN; }
    }

    private LocalDateTime parseTimestampLenient(String raw) {
        if (raw == null || raw.isBlank()) { return null; }
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try { return LocalDateTime.parse(raw, fmt); } catch (DateTimeParseException ignored) { }
        }
        try { return LocalDateTime.parse(raw); } catch (DateTimeParseException ignored) { }
        return null;
    }

    private AttendanceDto.RecordResponse toResponse(AttendanceRecord r) {
        return AttendanceDto.RecordResponse.builder()
                .id(r.getId())
                .employeeCode(r.getEmployeeCode())
                .staffId(r.getStaffId())
                .staffName(r.getStaffName())
                .punchTime(r.getPunchTime())
                .direction(r.getDirection() != null ? r.getDirection().name() : null)
                .source(r.getSource() != null ? r.getSource().name() : null)
                .deviceSerial(r.getDeviceSerial())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
