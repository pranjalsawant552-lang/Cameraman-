package com.cameraman.album.repository;

import com.cameraman.album.domain.Album;
import com.cameraman.album.domain.AlbumStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlbumRepository extends JpaRepository<Album, String> {
    Page<Album> findByClientId(String clientId, Pageable pageable);
    Page<Album> findByStatus(AlbumStatus status, Pageable pageable);
    List<Album> findByBookingId(String bookingId);
}
