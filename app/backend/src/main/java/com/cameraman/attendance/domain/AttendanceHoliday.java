package com.cameraman.attendance.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** A studio-wide paid holiday: nobody is marked absent, and any work done counts as overtime. */
@Entity
@Table(name = "attendance_holidays")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceHoliday extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
