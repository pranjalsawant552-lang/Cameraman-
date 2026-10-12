package com.cameraman.notification.service;

import com.cameraman.common.exception.BusinessException;
import com.cameraman.notification.domain.NotificationCategory;
import com.cameraman.notification.domain.NotificationPreference;
import com.cameraman.notification.repository.NotificationPreferenceRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPreferenceServiceTest {

    @Mock private NotificationPreferenceRepository repository;
    @Mock private UserRepository userRepository;
    private NotificationPreferenceService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository, userRepository);
        user = new User();
        user.setId("u1");
        user.setMarketingOptIn(true);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(repository.findByUserId("u1")).thenReturn(List.of());
        when(repository.findByUserIdAndCategory(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void typesMapToCategories() {
        assertThat(NotificationCategory.forType("BOOKING_CREATED")).isEqualTo(NotificationCategory.BOOKINGS);
        assertThat(NotificationCategory.forType("SELECTION_APPROVED")).isEqualTo(NotificationCategory.ORDER_UPDATES);
        assertThat(NotificationCategory.forType("GALLERY_PUBLISHED")).isEqualTo(NotificationCategory.GALLERY);
        assertThat(NotificationCategory.forType("SUGGESTIONS_PENDING")).isEqualTo(NotificationCategory.MARKETING);
        assertThat(NotificationCategory.forType("PAYMENT_RECEIVED")).isEqualTo(NotificationCategory.PAYMENTS);
        assertThat(NotificationCategory.forType("LEAVE_REQUESTED")).isEqualTo(NotificationCategory.SYSTEM);
        assertThat(NotificationCategory.forType(null)).isEqualTo(NotificationCategory.SYSTEM);
    }

    @Test
    void noStoredRow_meansEnabled() {
        assertThat(service.isEnabled("u1", "GALLERY_PUBLISHED")).isTrue();
    }

    @Test
    void optedOutCategory_isSuppressed() {
        NotificationPreference off = NotificationPreference.builder().userId("u1").category(NotificationCategory.GALLERY).enabled(false).build();
        when(repository.findByUserIdAndCategory("u1", NotificationCategory.GALLERY)).thenReturn(Optional.of(off));
        assertThat(service.isEnabled("u1", "GALLERY_PUBLISHED")).isFalse();
        assertThat(service.isEnabled("u1", "BOOKING_CREATED")).isTrue();
    }

    @Test
    void mandatoryCategories_ignoreStoredOptOut() {
        NotificationPreference off = NotificationPreference.builder().userId("u1").category(NotificationCategory.PAYMENTS).enabled(false).build();
        when(repository.findByUserIdAndCategory("u1", NotificationCategory.PAYMENTS)).thenReturn(Optional.of(off));
        assertThat(service.isEnabled("u1", "PAYMENT_RECEIVED")).isTrue();
    }

    @Test
    void marketing_followsAccountWideConsent() {
        user.setMarketingOptIn(false);
        assertThat(service.isEnabled("u1", "SUGGESTIONS_PENDING")).isFalse();
        assertThat(service.list("u1").stream().filter(v -> v.category().equals("MARKETING")).findFirst().orElseThrow().enabled()).isFalse();
    }

    @Test
    void cannotDisableMandatory_orUnknown() {
        assertThatThrownBy(() -> service.set("u1", "PAYMENTS", false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.set("u1", "NOPE", false)).isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void set_storesTheChoice() {
        service.set("u1", "GALLERY", false);
        ArgumentCaptor<NotificationPreference> cap = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().isEnabled()).isFalse();
        assertThat(cap.getValue().getCategory()).isEqualTo(NotificationCategory.GALLERY);
    }

    @Test
    void list_showsAllCategories_withMandatoryAlwaysOn() {
        var list = service.list("u1");
        assertThat(list).hasSize(NotificationCategory.values().length);
        assertThat(list.stream().filter(v -> v.mandatory()).allMatch(v -> v.enabled())).isTrue();
    }
}
