package com.cameraman.gallery.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/** One client who may see a gallery; {@code canSelect} also lets them pick photos (their own selection). */
@Entity
@Table(name = "gallery_viewers", uniqueConstraints = @UniqueConstraint(name = "uq_gallery_viewer", columnNames = {"gallery_id", "client_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GalleryViewer extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "gallery_id", nullable = false, length = 36)
    private String galleryId;

    @Column(name = "client_id", nullable = false, length = 36)
    private String clientId;

    @Column(name = "can_select", nullable = false)
    private boolean canSelect;
}
