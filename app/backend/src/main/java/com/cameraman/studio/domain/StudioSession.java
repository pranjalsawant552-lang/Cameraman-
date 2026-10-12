package com.cameraman.studio.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A booking for an in-studio session (portrait, birthday, product, casual, newborn,
 * maternity, etc — anything shot inside the studio's own premises). Deliberately a
 * separate entity from Booking/OutdoorShoot rather than a shared table with a
 * discriminator column: studio sessions and outdoor shoots have genuinely different
 * fields (room/set vs venue/travel), different calendars (room availability vs
 * photographer day-blocking), and the business wanted them operable as fully
 * independent modules, not a shared flow with a location flag.
 */
@Entity
@Table(name = "studio_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudioSession extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_number", unique = true, nullable = false, length = 30)
    private String sessionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "client_mobile", length = 15)
    private String clientMobile;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Column(name = "package_id")
    private String packageId;

    @Column(name = "package_name", length = 150)
    private String packageName;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** Which physical room/set at the studio, e.g. "Room A", "Cyclorama", "Room B". */
    @Column(name = "studio_room", length = 100)
    private String studioRoom;

    @Column(name = "backdrop_notes", columnDefinition = "TEXT")
    private String backdropNotes;

    @Column(name = "props_required", columnDefinition = "TEXT")
    private String propsRequired;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photographer_id")
    private User photographer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private User editor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StudioSessionStatus status = StudioSessionStatus.PENDING;

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
