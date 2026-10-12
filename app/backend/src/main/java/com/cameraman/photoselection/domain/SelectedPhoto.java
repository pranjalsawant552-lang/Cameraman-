package com.cameraman.photoselection.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "selected_photos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"selection_id", "photo_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SelectedPhoto {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selection_id", nullable = false)
    private PhotoSelection selection;

    @Column(name = "photo_id", nullable = false)
    private String photoId;

    @Column(name = "selected_at")
    @Builder.Default
    private LocalDateTime selectedAt = LocalDateTime.now();

    @Column(name = "is_favourite")
    @Builder.Default
    private boolean favourite = false;

    @Column(name = "notes", length = 500)
    private String notes;
}
