package com.cameraman.photoselection.repository;

import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.photoselection.domain.SelectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PhotoSelectionRepository extends JpaRepository<PhotoSelection, String> {
    Optional<PhotoSelection> findByAccessToken(String token);
    java.util.List<PhotoSelection> findByClientId(String clientId);
    Page<PhotoSelection> findByClientId(String clientId, Pageable pageable);
    Page<PhotoSelection> findByStatus(SelectionStatus status, Pageable pageable);
    Optional<PhotoSelection> findByGalleryIdAndClientId(String galleryId, String clientId);
    java.util.List<PhotoSelection> findByGalleryId(String galleryId);
    long countByStatus(SelectionStatus status);

    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM PhotoSelection s WHERE s.status = 'OPEN' " +
        "AND s.expiresAt IS NOT NULL AND s.expiresAt < CURRENT_TIMESTAMP " +
        "AND s.reminderSentAt IS NULL")
    java.util.List<PhotoSelection> findOverdueNeedingReminder();
}
