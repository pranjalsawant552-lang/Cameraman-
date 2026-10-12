package com.cameraman.gallery.repository;

import com.cameraman.gallery.domain.GalleryViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryViewerRepository extends JpaRepository<GalleryViewer, String> {
    List<GalleryViewer> findByGalleryId(String galleryId);
    boolean existsByGalleryIdAndClientId(String galleryId, String clientId);
    void deleteByGalleryIdAndClientIdIn(String galleryId, java.util.Collection<String> clientIds);
}
