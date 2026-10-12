package com.cameraman.booking.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A date the studio is fully closed for bookings (public holiday, festival,
 * planned maintenance, etc). Checked by BookingService.checkAvailability
 * (returns all slots unavailable with this reason) and by createBooking
 * (rejects new bookings outright), so blocking a date is enforced
 * consistently everywhere a booking could be created — client self-booking,
 * admin manual booking, and the availability check the calendar UI reads.
 */
@Entity
@Table(name = "blocked_dates", uniqueConstraints = @UniqueConstraint(columnNames = "blocked_date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BlockedDate extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "blocked_date", nullable = false)
    private LocalDate date;

    @Column(name = "reason", length = 200)
    private String reason;
}
