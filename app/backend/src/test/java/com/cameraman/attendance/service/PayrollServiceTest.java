package com.cameraman.attendance.service;

import com.cameraman.attendance.dto.AttendanceDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollServiceTest {

    private AttendanceDto.MonthlyReport report(int working, int absent, int half, int late, long overtimeMinutes) {
        return AttendanceDto.MonthlyReport.builder()
                .staffId("s1").staffName("Anita Rao").year(2025).month(3)
                .workingDays(working).absentDays(absent).halfDays(half).lateDays(late)
                .presentDays(working - absent - half).leaveDays(0).weeklyOffDays(4).holidayDays(0)
                .workedMinutes(working * 480L).overtimeMinutes(overtimeMinutes).lateMinutes(0)
                .build();
    }

    @Test
    void deductsAbsencesHalfDaysAndLatePenalty() {
        // 26 working days, 1 absent, 2 half days (=1 day), 7 lates at 3 per half day => 2 x 0.5 = 1 day. Unpaid = 3 days.
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(26, 1, 2, 7, 0),
                new BigDecimal("26000"), null, 3);
        assertThat(row.getUnpaidDays()).isEqualTo(3.0);
        assertThat(row.getLatePenaltyDays()).isEqualTo(1.0);
        assertThat(row.getPerDayRate()).isEqualByComparingTo("1000.00");
        assertThat(row.getDeduction()).isEqualByComparingTo("3000.00");
        assertThat(row.getNetPay()).isEqualByComparingTo("23000.00");
    }

    @Test
    void addsOvertimePay() {
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(26, 0, 0, 0, 150),
                new BigDecimal("26000"), new BigDecimal("200"), 3);
        assertThat(row.getOvertimeHours()).isEqualTo(2.5);
        assertThat(row.getOvertimePay()).isEqualByComparingTo("500.00");
        assertThat(row.getNetPay()).isEqualByComparingTo("26500.00");
    }

    @Test
    void latePenaltyCanBeTurnedOff() {
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(26, 0, 0, 9, 0), new BigDecimal("26000"), null, 0);
        assertThat(row.getLatePenaltyDays()).isZero();
        assertThat(row.getNetPay()).isEqualByComparingTo("26000.00");
    }

    @Test
    void deductionNeverExceedsSalary() {
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(10, 10, 0, 0, 0), new BigDecimal("5000"), null, 3);
        assertThat(row.getDeduction()).isEqualByComparingTo("5000.00");
        assertThat(row.getNetPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void missingSalaryLeavesMoneyBlankButKeepsDays() {
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(26, 2, 0, 0, 0), null, null, 3);
        assertThat(row.getMonthlySalary()).isNull();
        assertThat(row.getNetPay()).isNull();
        assertThat(row.getUnpaidDays()).isEqualTo(2.0);
    }

    @Test
    void csvStartsWithBomAndNeutralisesFormulas() {
        AttendanceDto.PayrollRow row = PayrollService.computeRow(report(26, 0, 0, 0, 0), new BigDecimal("100"), null, 3);
        row.setStaffName("=HYPERLINK(\"http://x\")");
        row.setDepartment("Ops, \"East\"");
        String csv = new String(PayrollService.toCsv(AttendanceDto.PayrollReport.builder()
                .year(2025).month(3).rows(List.of(row)).totalNetPay(BigDecimal.ZERO).build()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(csv).contains("\"Ops, \"\"East\"\"\"");
    }
}
