package com.cameraman.booking.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One row per stage transition on a booking's order pipeline — gives staff
 * (and, read-only, the client) a full history of when each stage was
 * reached and by whom. Mirrors the BookingNote pattern already used on
 * Booking.
 */
@Entity
@Table(name = "order_stage_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderStageHistory extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private OrderStage stage;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_by_name", length = 150)
    private String changedByName;
}
