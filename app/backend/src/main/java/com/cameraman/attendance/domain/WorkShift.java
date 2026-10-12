package com.cameraman.attendance.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/** A named working shift (for example "General 09:30-18:30" or "Event crew 11:00-20:00"). */
@Entity
@Table(name = "work_shifts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkShift extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "grace_minutes", nullable = false)
    @Builder.Default
    private int graceMinutes = 15;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
