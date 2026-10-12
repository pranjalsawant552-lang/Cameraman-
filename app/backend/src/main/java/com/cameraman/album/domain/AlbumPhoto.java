package com.cameraman.album.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/** One photo placed in an album, with its order and (optionally) the page it goes on. */
@Entity
@Table(name = "album_photos", uniqueConstraints = @UniqueConstraint(name = "uq_album_photo", columnNames = {"album_id", "photo_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlbumPhoto extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "album_id", nullable = false, length = 36)
    private String albumId;

    @Column(name = "photo_id", nullable = false, length = 36)
    private String photoId;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "note", length = 300)
    private String note;
}
