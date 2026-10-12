package com.cameraman.gallery.repository;

import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.domain.GalleryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GalleryRepository extends JpaRepository<Gallery, String> {
    Optional<Gallery> findByAccessToken(String token);
    Page<Gallery> findByClientId(String clientId, Pageable pageable);
    Page<Gallery> findByClientIdAndStatus(String clientId, GalleryStatus status, Pageable pageable);
    Page<Gallery> findByStatus(GalleryStatus status, Pageable pageable);

    /** Published galleries a client may open: their own, plus ones staff shared with them. */
    @Query("SELECT g FROM Gallery g WHERE g.status = :status AND (g.clientId = :clientId OR g.id IN " +
           "(SELECT v.galleryId FROM GalleryViewer v WHERE v.clientId = :clientId))")
    Page<Gallery> findVisibleToClient(@Param("clientId") String clientId, @Param("status") GalleryStatus status, Pageable pageable);
    Page<Gallery> findByBookingId(String bookingId, Pageable pageable);
    java.util.List<Gallery> findAllByBookingId(String bookingId);

    @Query("SELECT g FROM Gallery g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(g.clientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))")
    Page<Gallery> searchGalleries(@Param("search") String search, Pageable pageable);

    long countByStatus(GalleryStatus status);

    @Query("SELECT g FROM Gallery g WHERE g.originalsExpiryDays IS NOT NULL AND g.originalsExpiredAt IS NULL AND g.publishedAt IS NOT NULL")
    java.util.List<Gallery> findPendingOriginalsExpiry();
}
