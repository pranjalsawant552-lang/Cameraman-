package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.DayAttendanceStatus;
import com.cameraman.attendance.service.AttendanceCalculator.DayResult;
import com.cameraman.attendance.service.AttendanceCalculator.Rules;
import com.cameraman.attendance.service.AttendanceCalculator.Schedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceCalculatorTest {

    // 2025-03-03 is a Monday, 2025-03-09 a Sunday.
    private static final LocalDate MON = LocalDate.of(2025, 3, 3);
    private static final LocalDate SUN = LocalDate.of(2025, 3, 9);
    private static final LocalDate TODAY = LocalDate.of(2025, 3, 31);

    private static final Schedule SCHEDULE =
            new Schedule(LocalTime.of(9, 30), LocalTime.of(18, 30), 15, Set.of(DayOfWeek.SUNDAY));
    private static final Rules RULES = new Rules(270, 30);

    private static LocalDateTime at(LocalDate d, int h, int m) { return d.atTime(h, m); }

    private DayResult eval(LocalDate day, boolean holiday, boolean leave, LocalDateTime... punches) {
        return AttendanceCalculator.evaluate(day, TODAY, SCHEDULE, RULES, holiday, leave, List.of(punches));
    }

    @Test
    void futureDayIsFuture() {
        DayResult r = AttendanceCalculator.evaluate(TODAY.plusDays(1), TODAY, SCHEDULE, RULES, false, false, List.of());
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.FUTURE);
    }

    @Test
    void onTimeFullDayIsPresent() {
        DayResult r = eval(MON, false, false, at(MON, 9, 30), at(MON, 18, 30));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.PRESENT);
        assertThat(r.workedMinutes()).isEqualTo(540);
        assertThat(r.overtimeMinutes()).isZero();
        assertThat(r.lateMinutes()).isZero();
    }

    @Test
    void arrivalWithinGraceIsNotLate() {
        assertThat(eval(MON, false, false, at(MON, 9, 45), at(MON, 18, 30)).status()).isEqualTo(DayAttendanceStatus.PRESENT);
    }

    @Test
    void arrivalAfterGraceIsLateAndCountsFromShiftStart() {
        DayResult r = eval(MON, false, false, at(MON, 10, 0), at(MON, 18, 30));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.LATE);
        assertThat(r.lateMinutes()).isEqualTo(30);
    }

    @Test
    void noPunchIsAbsent() {
        assertThat(eval(MON, false, false).status()).isEqualTo(DayAttendanceStatus.ABSENT);
    }

    @Test
    void approvedLeaveBeatsAbsent() {
        assertThat(eval(MON, false, true).status()).isEqualTo(DayAttendanceStatus.ON_LEAVE);
    }

    @Test
    void shortDayIsHalfDay() {
        DayResult r = eval(MON, false, false, at(MON, 9, 30), at(MON, 12, 0));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.HALF_DAY);
        assertThat(r.earlyLeaveMinutes()).isEqualTo(390);
    }

    @Test
    void halfDayRuleCanBeTurnedOff() {
        DayResult r = AttendanceCalculator.evaluate(MON, TODAY, SCHEDULE, new Rules(0, 30), false, false,
                List.of(at(MON, 9, 30), at(MON, 12, 0)));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.PRESENT);
    }

    @Test
    void singlePunchIsNotHalfDayButFlagsMissingOut() {
        DayResult r = eval(MON, false, false, at(MON, 9, 30));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.PRESENT);
        assertThat(r.missingOut()).isTrue();
        assertThat(r.workedMinutes()).isZero();
    }

    @Test
    void overtimeCountsOnlyAtOrAboveMinimum() {
        assertThat(eval(MON, false, false, at(MON, 9, 30), at(MON, 19, 0)).overtimeMinutes()).isEqualTo(30);
        assertThat(eval(MON, false, false, at(MON, 9, 30), at(MON, 18, 50)).overtimeMinutes()).isZero();
    }

    @Test
    void weeklyOffIsNotAbsent() {
        assertThat(eval(SUN, false, false).status()).isEqualTo(DayAttendanceStatus.WEEKLY_OFF);
    }

    @Test
    void workingOnWeeklyOffIsAllOvertime() {
        DayResult r = eval(SUN, false, false, at(SUN, 10, 0), at(SUN, 14, 0));
        assertThat(r.status()).isEqualTo(DayAttendanceStatus.WEEKLY_OFF);
        assertThat(r.overtimeMinutes()).isEqualTo(240);
    }

    @Test
    void holidayBeatsLeaveAndAbsence() {
        assertThat(eval(MON, true, true).status()).isEqualTo(DayAttendanceStatus.HOLIDAY);
    }
}
