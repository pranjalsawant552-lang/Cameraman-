package com.cameraman.attendance.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One row per staff member overriding the studio defaults: which shift they work, their own
 * weekly off day(s), and the pay figures the payroll export needs. A staff member with no row
 * (or null fields) simply follows the studio-wide settings.
 */
@Entity
@Table(name = "staff_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffSchedule extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "staff_id", nullable = false, unique = true, length = 36)
    private String staffId;

    @Column(name = "shift_id", length = 36)
    private String shiftId;

    /** Comma separated DayOfWeek names, e.g. "SUNDAY" or "FRIDAY,SUNDAY". Null = studio default. */
    @Column(name = "weekly_off_days", length = 80)
    private String weeklyOffDays;

    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    @Column(name = "overtime_rate_per_hour", precision = 10, scale = 2)
    private BigDecimal overtimeRatePerHour;
}
