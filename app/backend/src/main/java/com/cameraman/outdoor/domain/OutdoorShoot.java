package com.cameraman.outdoor.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A booking for an on-location shoot (wedding, pre-wedding, corporate on-site,
 * events, destination shoots) — anything shot away from the studio's own premises.
 * Kept as a fully separate entity/module from StudioSession/Booking: outdoor shoots
 * have genuinely different concerns (venue/travel logistics, weather risk, multi-day
 * span for weddings) that don't apply to an in-studio session, and the studio wanted
 * this operable as an independent module rather than a location flag on one shared
 * booking flow.
 */
@Entity
@Table(name = "outdoor_shoots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutdoorShoot extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "shoot_number", unique = true, nullable = false, length = 30)
    private String shootNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "client_mobile", length = 15)
    private String clientMobile;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutdoorShootCategory category;

    @Column(name = "package_id")
    private String packageId;

    @Column(name = "package_name", length = 150)
    private String packageName;

    @Column(name = "shoot_date", nullable = false)
    private LocalDate shootDate;

    /** For multi-day coverage (e.g. a 3-day wedding). Null = single-day shoot. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "venue_address", columnDefinition = "TEXT")
    private String venueAddress;

    @Column(name = "venue_city", length = 100)
    private String venueCity;

    @Column(name = "venue_pincode", length = 10)
    private String venuePincode;

    @Column(name = "travel_distance_km")
    private Integer travelDistanceKm;

    @Column(name = "travel_cost", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal travelCost = BigDecimal.ZERO;

    @Column(name = "weather_dependent")
    @Builder.Default
    private boolean weatherDependent = false;

    @Column(name = "backup_plan_notes", columnDefinition = "TEXT")
    private String backupPlanNotes;

    /** Freeform checklist text, e.g. "Drone, 2x lighting kit, reflectors, backup batteries". */
    @Column(name = "equipment_checklist", columnDefinition = "TEXT")
    private String equipmentChecklist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photographer_id")
    private User photographer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private User editor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutdoorShootStatus status = OutdoorShootStatus.PENDING;

    @Column(name = "total_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "advance_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    public BigDecimal getBalanceAmount() {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal advance = advanceAmount != null ? advanceAmount : BigDecimal.ZERO;
        return total.subtract(advance);
    }

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancelled_reason", length = 300)
    private String cancelledReason;

    @Column(name = "rescheduled_count")
    @Builder.Default
    private int rescheduledCount = 0;
}
