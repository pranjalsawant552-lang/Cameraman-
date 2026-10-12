package com.cameraman.outdoor.repository;

import com.cameraman.outdoor.domain.OutdoorShoot;
import com.cameraman.outdoor.domain.OutdoorShootStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OutdoorShootRepository extends JpaRepository<OutdoorShoot, String> {
    List<OutdoorShoot> findByShootDate(LocalDate date);
    List<OutdoorShoot> findByShootDateBetween(LocalDate from, LocalDate to);
    Page<OutdoorShoot> findByClientId(String clientId, Pageable pageable);
    Page<OutdoorShoot> findByStatus(OutdoorShootStatus status, Pageable pageable);
    long countByStatus(OutdoorShootStatus status);

    @Query("SELECT COUNT(o) FROM OutdoorShoot o WHERE o.shootDate = CURRENT_DATE AND o.status <> 'CANCELLED'")
    long countTodayShoots();

    /** A photographer can't be double-booked for a whole travel day, even across
     *  different venues — this checks by photographer + date range, not just exact
     *  time overlap like the studio room check does. */
    @Query("SELECT o FROM OutdoorShoot o WHERE o.photographer.id = :photographerId AND o.status <> 'CANCELLED' " +
           "AND o.shootDate <= :date AND (o.endDate IS NULL AND o.shootDate = :date OR o.endDate >= :date)")
    List<OutdoorShoot> findByPhotographerAndDate(String photographerId, LocalDate date);

    // See the identical comment on StudioSessionRepository.search() — same bug, same fix:
    // an un-cast nullable :search parameter inside LOWER(...) let Postgres infer the
    // parameter type as bytea instead of text, which LOWER() has no overload for.
    @Query("SELECT o FROM OutdoorShoot o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:category IS NULL OR o.category = :category) AND " +
           "(:search IS NULL OR LOWER(o.clientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR LOWER(o.shootNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR LOWER(o.venueCity) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<OutdoorShoot> search(OutdoorShootStatus status, com.cameraman.outdoor.domain.OutdoorShootCategory category, String search, Pageable pageable);
}
