package com.cameraman.attendance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceDevice {

    @Id
    @Column(length = 100)
    private String serial;

    @Column(length = 100)
    private String label;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "first_seen_at", nullable = false)
    @Builder.Default
    private LocalDateTime firstSeenAt = LocalDateTime.now();

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    @Column(name = "punch_count", nullable = false)
    private long punchCount;
}
