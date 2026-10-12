package com.cameraman.notification.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.notification.domain.NotificationCategory;
import com.cameraman.notification.domain.NotificationPreference;
import com.cameraman.notification.repository.NotificationPreferenceRepository;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final UserRepository userRepository;

    public record PreferenceView(String category, String label, String description, boolean mandatory, boolean enabled) {}

    @Transactional(readOnly = true)
    public List<PreferenceView> list(String userId) {
        Map<NotificationCategory, Boolean> stored = new HashMap<>();
        repository.findByUserId(userId).forEach(p -> stored.put(p.getCategory(), p.isEnabled()));
        boolean marketingConsent = userRepository.findById(userId).map(u -> u.isMarketingOptIn()).orElse(true);
        return Arrays.stream(NotificationCategory.values()).map(c -> {
            boolean enabled = c.isMandatory() || stored.getOrDefault(c, true);
            if (c == NotificationCategory.MARKETING && !marketingConsent) enabled = false; // account-wide consent wins
            return new PreferenceView(c.name(), c.getLabel(), c.getDescription(), c.isMandatory(), enabled);
        }).toList();
    }

    public void set(String userId, String categoryName, boolean enabled) {
        NotificationCategory category;
        try {
            category = NotificationCategory.valueOf(categoryName);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Unknown notification category", "UNKNOWN_CATEGORY");
        }
        if (category.isMandatory() && !enabled) {
            throw new BusinessException(category.getLabel() + " notifications can't be turned off", "MANDATORY_CATEGORY");
        }
        NotificationPreference pref = repository.findByUserIdAndCategory(userId, category)
                .orElse(NotificationPreference.builder().userId(userId).category(category).build());
        pref.setEnabled(enabled);
        pref.setUpdatedAt(LocalDateTime.now());
        repository.save(pref);
    }

    /** True when this user should receive a notification of the given type. Missing rows mean "yes". */
    @Transactional(readOnly = true)
    public boolean isEnabled(String userId, String type) {
        NotificationCategory category = NotificationCategory.forType(type);
        if (category.isMandatory()) return true;
        if (category == NotificationCategory.MARKETING
                && !userRepository.findById(userId).map(u -> u.isMarketingOptIn()).orElse(true)) {
            return false;
        }
        return repository.findByUserIdAndCategory(userId, category).map(NotificationPreference::isEnabled).orElse(true);
    }
}
