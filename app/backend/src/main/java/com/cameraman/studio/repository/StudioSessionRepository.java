package com.cameraman.studio.repository;

import com.cameraman.studio.domain.StudioSession;
import com.cameraman.studio.domain.StudioSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface StudioSessionRepository extends JpaRepository<StudioSession, String> {
    List<StudioSession> findBySessionDate(LocalDate date);
    List<StudioSession> findBySessionDateBetween(LocalDate from, LocalDate to);
    Page<StudioSession> findByClientId(String clientId, Pageable pageable);
    Page<StudioSession> findByStatus(StudioSessionStatus status, Pageable pageable);
    long countByStatus(StudioSessionStatus status);

    @Query("SELECT COUNT(s) FROM StudioSession s WHERE s.sessionDate = CURRENT_DATE AND s.status <> 'CANCELLED'")
    long countTodaySessions();

    // The `IS NULL OR LOWER(...)` idiom lets one query handle every combination of optional
    // filters without building a query string dynamically. But left as `:search` bare
    // inside LOWER(...), Hibernate/PgJDBC can't always infer a concrete parameter type
    // for it — Postgres occasionally resolves the ambiguous type as `bytea` instead of
    // text, and LOWER() has no bytea overload, so the query fails with "function
    // lower(bytea) does not exist" (this is what was actually happening — a parameter
    // type inference bug, not a logic error; the query itself was always correct SQL).
    // Explicitly casting the parameter with CAST(:search AS string) forces Hibernate to
    // emit an unambiguous `cast(? as varchar)` in the generated SQL, which removes the
    // inference step entirely and fixes it for every combination of null/non-null filters.
    @Query("SELECT s FROM StudioSession s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:studioRoom IS NULL OR s.studioRoom = :studioRoom) AND " +
           "(:search IS NULL OR LOWER(s.clientName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR LOWER(s.sessionNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<StudioSession> search(StudioSessionStatus status, String studioRoom, String search, Pageable pageable);
}
