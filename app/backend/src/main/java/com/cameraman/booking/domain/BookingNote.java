package com.cameraman.booking.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "booking_notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingNote extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_internal")
    @Builder.Default
    private boolean internal = true;
}
