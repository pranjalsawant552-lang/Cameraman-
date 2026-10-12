package com.cameraman.photoselection.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "photo_selections", indexes = {
    @Index(name = "idx_selection_gallery", columnList = "gallery_id"),
    @Index(name = "idx_selection_client", columnList = "client_id"),
    @Index(name = "idx_selection_token", columnList = "access_token", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PhotoSelection extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "gallery_id", nullable = false)
    private String galleryId;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_name", length = 120)
    private String clientName;

    @Column(name = "access_token", unique = true, length = 100)
    private String accessToken;

    @Column(name = "selection_limit")
    private Integer selectionLimit;

    @Column(name = "selected_count")
    @Builder.Default
    private int selectedCount = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SelectionStatus status = SelectionStatus.OPEN;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "client_notes", columnDefinition = "TEXT")
    private String clientNotes;

    /** When the "you haven't picked your photos yet" WhatsApp reminder was sent —
     *  null means not sent yet. Prevents sending the same client the reminder every
     *  single day once they're overdue. */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @OneToMany(mappedBy = "selection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SelectedPhoto> selectedPhotos = new ArrayList<>();
}
