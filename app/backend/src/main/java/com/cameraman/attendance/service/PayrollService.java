package com.cameraman.attendance.service;

import com.cameraman.attendance.domain.StaffSchedule;
import com.cameraman.attendance.dto.AttendanceDto;
import com.cameraman.staff.domain.StaffProfile;
import com.cameraman.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns a month of attendance into pay figures.
 *
 *   unpaid days   = absences + 0.5 x half days + late penalty
 *   late penalty  = every N late arrivals (N = "lates per half day" setting) cost half a day; 0 turns it off
 *   per-day rate  = monthly salary / working days (month length minus weekly offs and holidays)
 *   deduction     = unpaid days x per-day rate, never more than the salary
 *   overtime pay  = overtime hours x the staff member's hourly overtime rate
 *   net pay       = salary - deduction + overtime pay
 *
 * Approved leave, weekly offs and holidays are paid. A month still in progress only counts absences
 * so far, which the report flags so nobody pays from it by mistake.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final AttendanceService attendanceService;
    private final AttendanceScheduleService scheduleService;
    private final StaffRepository staffRepository;

    public AttendanceDto.PayrollReport report(int year, int month) {
        List<AttendanceDto.MonthlyReport> reports = attendanceService.monthlyReportsForAll(year, month);
        YearMonth ym = YearMonth.of(year, month);
        AttendanceScheduleService.Context ctx = scheduleService.loadContext(ym.atDay(1), ym.atEndOfMonth());
        Map<String, StaffProfile> staff = staffRepository.findAll().stream()
                .collect(Collectors.toMap(StaffProfile::getId, s -> s));

        List<AttendanceDto.PayrollRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (AttendanceDto.MonthlyReport r : reports) {
            StaffSchedule pay = ctx.rawByStaff.get(r.getStaffId());
            AttendanceDto.PayrollRow row = computeRow(r,
                    pay != null ? pay.getMonthlySalary() : null,
                    pay != null ? pay.getOvertimeRatePerHour() : null,
                    ctx.latesPerHalfDay);
            StaffProfile sp = staff.get(r.getStaffId());
            if (sp != null) {
                row.setEmployeeId(sp.getEmployeeId());
                row.setDepartment(sp.getDepartment());
            }
            rows.add(row);
            if (row.getNetPay() != null) total = total.add(row.getNetPay());
        }
        rows.sort(Comparator.comparing(AttendanceDto.PayrollRow::getStaffName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return AttendanceDto.PayrollReport.builder()
                .year(year).month(month)
                .monthInProgress(ym.atEndOfMonth().isAfter(LocalDate.now().minusDays(1)))
                .rows(rows).totalNetPay(total)
                .build();
    }

    /** Pure arithmetic, no I/O — unit tested directly. A null salary leaves the money columns empty. */
    static AttendanceDto.PayrollRow computeRow(AttendanceDto.MonthlyReport r, BigDecimal salary,
                                               BigDecimal overtimeRate, int latesPerHalfDay) {
        double latePenalty = latesPerHalfDay > 0 ? (r.getLateDays() / latesPerHalfDay) * 0.5 : 0;
        double unpaid = r.getAbsentDays() + 0.5 * r.getHalfDays() + latePenalty;
        BigDecimal overtimePay = overtimeRate == null ? BigDecimal.ZERO
                : overtimeRate.multiply(BigDecimal.valueOf(r.getOvertimeMinutes()))
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        BigDecimal perDay = null, deduction = null, net = null;
        if (salary != null) {
            if (r.getWorkingDays() > 0) {
                perDay = salary.divide(BigDecimal.valueOf(r.getWorkingDays()), 2, RoundingMode.HALF_UP);
                deduction = salary.multiply(BigDecimal.valueOf(unpaid))
                        .divide(BigDecimal.valueOf(r.getWorkingDays()), 2, RoundingMode.HALF_UP);
                if (deduction.compareTo(salary) > 0) deduction = salary;
            } else {
                deduction = BigDecimal.ZERO;
            }
            net = salary.subtract(deduction).add(overtimePay);
        }
        return AttendanceDto.PayrollRow.builder()
                .staffId(r.getStaffId()).staffName(r.getStaffName())
                .workingDays(r.getWorkingDays()).presentDays(r.getPresentDays()).lateDays(r.getLateDays())
                .halfDays(r.getHalfDays()).absentDays(r.getAbsentDays()).leaveDays(r.getLeaveDays())
                .weeklyOffDays(r.getWeeklyOffDays()).holidayDays(r.getHolidayDays())
                .workedHours(round2(r.getWorkedMinutes() / 60.0))
                .overtimeHours(round2(r.getOvertimeMinutes() / 60.0))
                .unpaidDays(unpaid).latePenaltyDays(latePenalty)
                .monthlySalary(salary).perDayRate(perDay).deduction(deduction)
                .overtimePay(overtimePay).netPay(net)
                .build();
    }

    // ------------------------------------------------------------------ CSV

    public byte[] exportCsv(int year, int month) {
        return toCsv(report(year, month));
    }

    static byte[] toCsv(AttendanceDto.PayrollReport report) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel opens it as UTF-8
        sb.append(String.join(",", "Employee ID", "Name", "Department", "Working days", "Present", "Late", "Half days",
                "Absent", "Paid leave", "Weekly off", "Holidays", "Hours worked", "Overtime hours", "Unpaid days",
                "Monthly salary", "Per-day rate", "Deduction", "Overtime pay", "Net pay")).append("\r\n");
        for (AttendanceDto.PayrollRow r : report.getRows()) {
            sb.append(String.join(",",
                    cell(r.getEmployeeId()), cell(r.getStaffName()), cell(r.getDepartment()),
                    String.valueOf(r.getWorkingDays()), String.valueOf(r.getPresentDays()), String.valueOf(r.getLateDays()),
                    String.valueOf(r.getHalfDays()), String.valueOf(r.getAbsentDays()), String.valueOf(r.getLeaveDays()),
                    String.valueOf(r.getWeeklyOffDays()), String.valueOf(r.getHolidayDays()),
                    num(r.getWorkedHours()), num(r.getOvertimeHours()), num(r.getUnpaidDays()),
                    money(r.getMonthlySalary()), money(r.getPerDayRate()), money(r.getDeduction()),
                    money(r.getOvertimePay()), money(r.getNetPay()))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Quotes a text cell and neutralises spreadsheet formulas (a name like "=HYPERLINK(...)" must stay text). */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String num(double d) {
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal b) {
        return b == null ? "" : b.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
