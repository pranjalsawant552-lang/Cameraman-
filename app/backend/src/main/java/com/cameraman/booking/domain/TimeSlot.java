package com.cameraman.booking.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimeSlot extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_available")
    @Builder.Default
    private boolean available = true;

    @Column(name = "is_blocked")
    @Builder.Default
    private boolean blocked = false;

    @Column(name = "block_reason", length = 200)
    private String blockReason;

    @Column(name = "booking_id", length = 36)
    private String bookingId;

    @Column(name = "max_bookings")
    @Builder.Default
    private int maxBookings = 1;

    @Column(name = "current_bookings")
    @Builder.Default
    private int currentBookings = 0;
}
