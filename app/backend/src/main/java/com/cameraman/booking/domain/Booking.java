package com.cameraman.booking.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_date", columnList = "booking_date"),
    @Index(name = "idx_booking_client", columnList = "client_id"),
    @Index(name = "idx_booking_status", columnList = "status"),
    @Index(name = "idx_booking_number", columnList = "booking_number", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 20)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    // Walk-in client info (when no user account)
    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(name = "client_mobile", length = 15)
    private String clientMobile;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "package_id", length = 36)
    private String packageId;

    @Column(name = "package_name", length = 100)
    private String packageName;

    // Shooting category selected by the client: WEDDING, PRE_WEDDING, BIRTHDAY, CASUAL, etc.
    @Enumerated(EnumType.STRING)
    @Column(name = "shoot_category", nullable = false)
    @Builder.Default
    private PackageCategory shootCategory = PackageCategory.OTHER;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingType type = BookingType.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photographer_id")
    private User photographer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private User editor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    // ── Order/workflow pipeline (post-confirmation fulfilment tracking) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "order_stage")
    @Builder.Default
    private OrderStage orderStage = OrderStage.BOOKING_CONFIRMED;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "courier_provider", length = 100)
    private String courierProvider;

    @Column(name = "courier_tracking_number", length = 100)
    private String courierTrackingNumber;

    // Storage key/URL for the delivery-confirmation signature photo (uploaded by
    // staff on handover, same storage service used for gallery photos).
    @Column(name = "delivery_signature_key", length = 500)
    private String deliverySignatureKey;

    @Column(name = "delivered_at")
    private java.time.LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("createdAt ASC")
    private List<OrderStageHistory> stageHistory = new ArrayList<>();

    @Column(name = "venue", length = 300)
    private String venue;

    /** Whether the shoot happens in the studio (INDOOR) or at a venue/location (OUTDOOR). */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 10)
    @Builder.Default
    private LocationType locationType = LocationType.INDOOR;

    /** When the finished photos/album were marked ready for the client to collect. */
    @Column(name = "ready_for_pickup_at")
    private java.time.LocalDateTime readyForPickupAt;

    @Column(name = "picked_up_at")
    private java.time.LocalDateTime pickedUpAt;

    @Column(name = "pickup_reminder_count", nullable = false)
    @Builder.Default
    private int pickupReminderCount = 0;

    @Column(name = "last_pickup_reminder_at")
    private java.time.LocalDateTime lastPickupReminderAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "advance_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "balance_amount", precision = 10, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @Column(name = "rescheduled_from")
    private LocalDate rescheduledFrom;

    @Column(name = "rescheduled_count")
    @Builder.Default
    private int rescheduledCount = 0;

    @Column(name = "reminder_sent")
    @Builder.Default
    private boolean reminderSent = false;

    /** Whether this client is okay being re-contacted later for engagement suggestions
     *  (next-year birthday reminder, anniversary shoot, review request, etc). Defaults to
     *  true to match pre-existing behavior for historical bookings; new bookings should
     *  set this from an explicit checkbox rather than relying on the default. Checked by
     *  SuggestionEngineScheduler before generating or auto-sending any suggestion. */
    @Column(name = "marketing_opt_in", nullable = false)
    @Builder.Default
    private boolean marketingOptIn = true;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingNote> bookingNotes = new ArrayList<>();
}
