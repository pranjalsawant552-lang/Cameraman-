package com.cameraman.notification.controller;

import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import com.cameraman.notification.domain.Notification;
import com.cameraman.notification.dto.NotificationDto;
import com.cameraman.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDto.NotificationResponse>>> getMyNotifications(
            @CurrentUser String userId,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Notification> result;
        if (type != null && !type.isBlank()) {
            result = notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
        } else if (Boolean.TRUE.equals(unreadOnly)) {
            result = notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            result = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(result.map(this::toResponse))));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(@CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", notificationRepository.countByUserIdAndReadFalse(userId))));
    }

    @PostMapping("/mark-all-read")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAllRead(@CurrentUser String userId) {
        notificationRepository.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markRead(@CurrentUser String userId, @PathVariable String id) {
        int updated = notificationRepository.markAsRead(id, userId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Notification", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@CurrentUser String userId, @PathVariable String id) {
        long deleted = notificationRepository.deleteByIdAndUserId(id, userId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Notification", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success("Notification deleted"));
    }

    private NotificationDto.NotificationResponse toResponse(Notification n) {
        return NotificationDto.NotificationResponse.builder()
                .id(n.getId()).title(n.getTitle()).message(n.getMessage())
                .type(n.getType()).read(n.isRead())
                .entityType(n.getEntityType()).entityId(n.getEntityId())
                .createdAt(n.getCreatedAt()).build();
    }

    private final com.cameraman.notification.service.NotificationPreferenceService preferenceService;

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<java.util.List<com.cameraman.notification.service.NotificationPreferenceService.PreferenceView>>> preferences(
            @com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success(preferenceService.list(userId)));
    }

    public record PreferenceUpdate(String category, boolean enabled) {}

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updatePreference(@RequestBody PreferenceUpdate body,
            @com.cameraman.common.security.CurrentUser String userId) {
        preferenceService.set(userId, body.category(), body.enabled());
        return ResponseEntity.ok(ApiResponse.success("Preference saved"));
    }
}
