package com.cameraman.album.repository;

import com.cameraman.album.domain.AlbumPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, String> {
    List<AlbumPhoto> findByAlbumIdOrderByPositionAsc(String albumId);
    long countByAlbumId(String albumId);
    void deleteByAlbumId(String albumId);
}
