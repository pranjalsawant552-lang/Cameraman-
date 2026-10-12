package com.cameraman.notification.repository;

import com.cameraman.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<Notification> findByUserIdAndReadOrderByCreatedAtDesc(String userId, boolean read, Pageable pageable);
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type, Pageable pageable);
    long countByUserIdAndReadFalse(String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.read = false")
    void markAllAsRead(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.userId = :userId")
    int markAsRead(@Param("id") String id, @Param("userId") String userId);

    long deleteByIdAndUserId(String id, String userId);
}
