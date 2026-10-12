package com.cameraman.engagement.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.engagement.domain.BookingIntakeAnswer;
import com.cameraman.engagement.domain.IntakeFieldType;
import com.cameraman.engagement.domain.IntakeQuestion;
import com.cameraman.engagement.repository.BookingIntakeAnswerRepository;
import com.cameraman.engagement.repository.IntakeQuestionRepository;
import com.cameraman.package_mgmt.domain.PackageCategory;
import com.cameraman.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntakeQuestionServiceTest {

    @Mock private IntakeQuestionRepository questionRepository;
    @Mock private BookingIntakeAnswerRepository answerRepository;
    @Mock private BookingRepository bookingRepository;

    private IntakeQuestionService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new IntakeQuestionService(questionRepository, answerRepository, bookingRepository);
        User owner = new User();
        owner.setId("owner");
        booking = Booking.builder().id("b1").client(owner).shootCategory(PackageCategory.WEDDING).build();
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));
        when(answerRepository.findByBookingIdAndQuestionKey(any(), any())).thenReturn(Optional.empty());
    }

    private IntakeQuestion q(String key, IntakeFieldType type, boolean required, String options) {
        return IntakeQuestion.builder().id(key).questionKey(key).questionText(key + "?")
                .category(PackageCategory.WEDDING).fieldType(type).required(required).options(options).active(true).build();
    }

    private void questions(IntakeQuestion... qs) {
        when(questionRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(PackageCategory.WEDDING))
                .thenReturn(List.of(qs));
    }

    @Test
    void owner_canSaveAnswers() {
        questions(q("guests", IntakeFieldType.NUMBER, true, null));
        service.submitAnswers("b1", Map.of("guests", "120"), "owner", false);
        verify(answerRepository).save(any(BookingIntakeAnswer.class));
    }

    @Test
    void otherClient_cannotAnswer_andSeesNotFound() {
        questions(q("guests", IntakeFieldType.NUMBER, false, null));
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of("guests", "5"), "intruder", false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void otherClient_cannotReadAnswers() {
        assertThatThrownBy(() -> service.getAnswers("b1", "intruder", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staff_canReadAnyBookingsAnswers() {
        when(answerRepository.findByBookingId("b1")).thenReturn(List.of());
        assertThat(service.getAnswers("b1", "staff", true)).isEmpty();
    }

    @Test
    void client_missingRequiredAnswer_isRejected() {
        questions(q("venue", IntakeFieldType.TEXT, true, null));
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of(), "owner", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("venue?");
    }

    @Test
    void staff_mayLeaveRequiredBlank() {
        questions(q("venue", IntakeFieldType.TEXT, true, null));
        service.submitAnswers("b1", Map.of(), "staff", true);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void number_mustBeNumeric() {
        questions(q("guests", IntakeFieldType.NUMBER, false, null));
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of("guests", "many"), "owner", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void date_mustBeIso() {
        questions(q("when", IntakeFieldType.DATE, false, null));
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of("when", "tomorrow"), "owner", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void select_mustMatchAnOption_caseInsensitive() {
        questions(q("style", IntakeFieldType.SELECT, false, "Candid, Traditional"));
        service.submitAnswers("b1", Map.of("style", "candid"), "owner", false);
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of("style", "Cartoon"), "owner", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownKeys_areIgnored() {
        questions(q("guests", IntakeFieldType.NUMBER, false, null));
        service.submitAnswers("b1", Map.of("hacker", "<script>"), "owner", false);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void overlongAnswer_isRejected() {
        questions(q("notes", IntakeFieldType.TEXT, false, null));
        assertThatThrownBy(() -> service.submitAnswers("b1", Map.of("notes", "x".repeat(2001)), "owner", false))
                .isInstanceOf(BusinessException.class);
    }
}
