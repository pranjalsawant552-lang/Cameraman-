package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.DayAttendanceStatus;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The attendance rules in one place, with no database or Spring in sight so they can be tested
 * directly. Given one staff member's schedule and punches for one day, work out the day's status
 * and the minutes worked, late, left early and overtime.
 *
 * Rules, in order:
 *  1. After today -> FUTURE.
 *  2. A studio holiday -> HOLIDAY; the staff member's weekly off day -> WEEKLY_OFF. If they punched
 *     in and out anyway, the whole worked time counts as overtime (when it reaches the minimum).
 *  3. Approved leave -> ON_LEAVE.
 *  4. No punch -> ABSENT.
 *  5. First punch to last punch is the time worked (needs two punches; a single punch is "missing out").
 *     Worked less than the half-day threshold -> HALF_DAY.
 *  6. First punch later than shift start + grace -> LATE, otherwise PRESENT.
 *  Overtime is the time after shift end, counted only when it reaches the minimum overtime minutes.
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {}

    public record Schedule(LocalTime start, LocalTime end, int graceMinutes, Set<DayOfWeek> weeklyOff) {}

    /** halfDayMinutes: worked time below this is a half day (0 = rule off). minOvertimeMinutes: smallest overtime that counts. */
    public record Rules(int halfDayMinutes, int minOvertimeMinutes) {}

    public record DayResult(DayAttendanceStatus status, LocalDateTime firstPunch, LocalDateTime lastPunch,
                            int punchCount, int workedMinutes, int lateMinutes, int earlyLeaveMinutes,
                            int overtimeMinutes, boolean missingOut) {}

    public static DayResult evaluate(LocalDate day, LocalDate today, Schedule schedule, Rules rules,
                                     boolean holiday, boolean onLeave, List<LocalDateTime> punches) {
        LocalDateTime first = punches.stream().min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime last = punches.stream().max(Comparator.naturalOrder()).orElse(null);
        int count = punches.size();
        boolean hasPair = count >= 2 && first != null && !first.equals(last);
        int worked = hasPair ? (int) Duration.between(first, last).toMinutes() : 0;
        boolean missingOut = count == 1;

        if (day.isAfter(today)) {
            return new DayResult(DayAttendanceStatus.FUTURE, first, last, count, 0, 0, 0, 0, false);
        }

        boolean weeklyOff = schedule.weeklyOff().contains(day.getDayOfWeek());
        if (holiday || weeklyOff) {
            int overtime = (hasPair && worked >= rules.minOvertimeMinutes()) ? worked : 0;
            return new DayResult(holiday ? DayAttendanceStatus.HOLIDAY : DayAttendanceStatus.WEEKLY_OFF,
                    first, last, count, worked, 0, 0, overtime, missingOut);
        }
        if (onLeave) {
            return new DayResult(DayAttendanceStatus.ON_LEAVE, first, last, count, worked, 0, 0, 0, missingOut);
        }
        if (first == null) {
            return new DayResult(DayAttendanceStatus.ABSENT, null, null, 0, 0, 0, 0, 0, false);
        }

        LocalTime lateAfter = schedule.start().plusMinutes(schedule.graceMinutes());
        boolean late = first.toLocalTime().isAfter(lateAfter);
        int lateMinutes = late ? (int) Duration.between(schedule.start(), first.toLocalTime()).toMinutes() : 0;

        int early = 0;
        int overtime = 0;
        if (hasPair) {
            LocalTime lastTime = last.toLocalTime();
            if (lastTime.isBefore(schedule.end())) {
                early = (int) Duration.between(lastTime, schedule.end()).toMinutes();
            } else if (lastTime.isAfter(schedule.end())) {
                int over = (int) Duration.between(schedule.end(), lastTime).toMinutes();
                overtime = over >= rules.minOvertimeMinutes() ? over : 0;
            }
        }

        DayAttendanceStatus status;
        if (hasPair && rules.halfDayMinutes() > 0 && worked < rules.halfDayMinutes()) {
            status = DayAttendanceStatus.HALF_DAY;
        } else if (late) {
            status = DayAttendanceStatus.LATE;
        } else {
            status = DayAttendanceStatus.PRESENT;
        }
        return new DayResult(status, first, last, count, worked, lateMinutes, early, overtime, missingOut);
    }
}
