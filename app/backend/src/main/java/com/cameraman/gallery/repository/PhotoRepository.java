package com.cameraman.gallery.repository;

import com.cameraman.gallery.domain.Photo;
import com.cameraman.gallery.domain.PhotoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, String> {
    Page<Photo> findByGalleryIdAndStatus(String galleryId, PhotoStatus status, Pageable pageable);
    List<Photo> findByGalleryIdOrderBySortOrderAsc(String galleryId);
    long countByGalleryId(String galleryId);
    long countByGalleryIdAndStatus(String galleryId, PhotoStatus status);
    long countByGalleryIdAndStatusNot(String galleryId, PhotoStatus status);

    @Query("SELECT p FROM Photo p JOIN FETCH p.gallery WHERE p.id = :id")
    java.util.Optional<Photo> findWithGalleryById(@Param("id") String id);

    List<Photo> findByStatusAndCreatedAtBefore(PhotoStatus status, java.time.LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE Photo p SET p.status = :status WHERE p.gallery.id = :galleryId AND p.status = :oldStatus")
    int bulkUpdateStatus(@Param("galleryId") String galleryId,
                         @Param("oldStatus") PhotoStatus oldStatus,
                         @Param("status") PhotoStatus newStatus);

    @Query("SELECT SUM(p.fileSizeBytes) FROM Photo p WHERE p.gallery.id = :galleryId")
    Long sumFileSizeByGallery(@Param("galleryId") String galleryId);

    List<Photo> findByGalleryIdAndStatusOrderBySortOrderAsc(String galleryId, PhotoStatus status);

    java.util.Optional<Photo> findFirstByGalleryIdAndUploadFingerprintOrderByCreatedAtAsc(String galleryId, String uploadFingerprint);

    boolean existsByGalleryIdAndUploadFingerprint(String galleryId, String uploadFingerprint);
}
