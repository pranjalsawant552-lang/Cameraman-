package com.cameraman.photoselection.repository;

import com.cameraman.photoselection.domain.SelectedPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SelectedPhotoRepository extends JpaRepository<SelectedPhoto, String> {
    List<SelectedPhoto> findBySelectionId(String selectionId);
    Optional<SelectedPhoto> findBySelectionIdAndPhotoId(String selectionId, String photoId);
    boolean existsBySelectionIdAndPhotoId(String selectionId, String photoId);
    long countBySelectionId(String selectionId);
    void deleteBySelectionIdAndPhotoId(String selectionId, String photoId);
}
