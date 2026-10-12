package com.cameraman.audit.repository;

import com.cameraman.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    Page<AuditLog> findByPerformedBy(String user, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
    Page<AuditLog> findByAction(String action, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
            "LOWER(a.action) LIKE LOWER(CONCAT('%', CAST(:term AS string), '%')) OR " +
            "LOWER(a.entityType) LIKE LOWER(CONCAT('%', CAST(:term AS string), '%')) OR " +
            "LOWER(a.performedBy) LIKE LOWER(CONCAT('%', CAST(:term AS string), '%')) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("term") String term, Pageable pageable);
}
