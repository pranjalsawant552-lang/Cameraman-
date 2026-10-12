package com.cameraman.notification.repository;

import com.cameraman.notification.domain.NotificationCategory;
import com.cameraman.notification.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
    List<NotificationPreference> findByUserId(String userId);
    Optional<NotificationPreference> findByUserIdAndCategory(String userId, NotificationCategory category);
}
