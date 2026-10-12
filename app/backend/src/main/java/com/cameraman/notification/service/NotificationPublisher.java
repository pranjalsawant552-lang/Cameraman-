package com.cameraman.notification.service;

import com.cameraman.notification.domain.Notification;
import com.cameraman.notification.dto.NotificationDto;
import com.cameraman.notification.repository.NotificationRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The in-app notification center existed as a table + a "list mine" endpoint,
 * but nothing anywhere in the app ever actually created a row — the feature
 * was wired up on the read side only. This is the write side: called from
 * business services when something notification-worthy happens, it persists
 * a Notification per recipient (so it still shows up next time they open the
 * app) and, if they're currently connected, pushes it immediately over the
 * WebSocket channel so the UI updates live instead of waiting for the next
 * poll of GET /notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationPreferenceService preferenceService;

    /** Notify one specific user (e.g. "your leave request was approved"). */
    @Async
    @Transactional
    public void notifyUser(String userId, String type, String title, String message,
                            String entityType, String entityId) {
        if (userId == null || !preferenceService.isEnabled(userId, type)) {
            return; // no recipient, or they switched this category off
        }
        Notification saved = save(userId, type, title, message, entityType, entityId);
        pushLive(userId, saved);
    }

    /** Notify every active user holding any of the given roles (e.g. new booking -> staff). */
    @Async
    @Transactional
    public void notifyRoles(List<String> roleNames, String type, String title, String message,
                             String entityType, String entityId) {
        List<User> recipients = userRepository.findByRoleNameIn(roleNames);
        for (User recipient : recipients) {
            if (!preferenceService.isEnabled(recipient.getId(), type)) continue;
            Notification saved = save(recipient.getId(), type, title, message, entityType, entityId);
            pushLive(recipient.getId(), saved);
        }
        // Also drop it on the shared admin feed topic so an open dashboard (which may be
        // showing an aggregate, not a per-user inbox) can refresh live too.
        try {
            messagingTemplate.convertAndSend("/topic/admin-feed", toResponse(
                    Notification.builder().title(title).message(message).type(type)
                            .entityType(entityType).entityId(entityId)
                            .createdAt(java.time.LocalDateTime.now()).build()));
        } catch (Exception e) {
            log.debug("Could not broadcast to admin feed: {}", e.getMessage());
        }
    }

    private Notification save(String userId, String type, String title, String message,
                               String entityType, String entityId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .channel("IN_APP")
                .entityType(entityType)
                .entityId(entityId)
                .build();
        return notificationRepository.save(notification);
    }

    private void pushLive(String userId, Notification notification) {
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", toResponse(notification));
        } catch (Exception e) {
            // The user simply may not be connected right now — the row is already persisted
            // above, so they'll see it next time they load /notifications. Not a failure.
            log.debug("Could not push live notification to user {}: {}", userId, e.getMessage());
        }
    }

    private NotificationDto.NotificationResponse toResponse(Notification n) {
        return NotificationDto.NotificationResponse.builder()
                .id(n.getId()).title(n.getTitle()).message(n.getMessage())
                .type(n.getType()).read(n.isRead())
                .entityType(n.getEntityType()).entityId(n.getEntityId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
