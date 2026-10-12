package com.cameraman.engagement.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.engagement.domain.BookingIntakeAnswer;
import com.cameraman.engagement.domain.IntakeFieldType;
import com.cameraman.engagement.domain.IntakeQuestion;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.repository.BookingIntakeAnswerRepository;
import com.cameraman.engagement.repository.IntakeQuestionRepository;
import com.cameraman.package_mgmt.domain.PackageCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class IntakeQuestionService {

    private final IntakeQuestionRepository questionRepository;
    private final BookingIntakeAnswerRepository answerRepository;
    private final BookingRepository bookingRepository;

    public EngagementDto.QuestionResponse createQuestion(EngagementDto.QuestionRequest request) {
        IntakeQuestion question = IntakeQuestion.builder()
                .category(PackageCategory.valueOf(request.getCategory()))
                .questionKey(request.getQuestionKey())
                .questionText(request.getQuestionText())
                .fieldType(IntakeFieldType.valueOf(request.getFieldType()))
                .options(request.getOptions())
                .required(request.isRequired())
                .sortOrder(request.getSortOrder())
                .active(true)
                .build();
        return toResponse(questionRepository.save(question));
    }

    public EngagementDto.QuestionResponse updateQuestion(String id, EngagementDto.QuestionRequest request) {
        IntakeQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intake question", "id", id));
        question.setCategory(PackageCategory.valueOf(request.getCategory()));
        question.setQuestionText(request.getQuestionText());
        question.setFieldType(IntakeFieldType.valueOf(request.getFieldType()));
        question.setOptions(request.getOptions());
        question.setRequired(request.isRequired());
        question.setSortOrder(request.getSortOrder());
        return toResponse(questionRepository.save(question));
    }

    public void setActive(String id, boolean active) {
        IntakeQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intake question", "id", id));
        question.setActive(active);
        questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public List<EngagementDto.QuestionResponse> listAll() {
        return questionRepository.findAllByOrderByCategoryAscSortOrderAsc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    /** The active questions a client/staff should be asked for a given shoot category. */
    @Transactional(readOnly = true)
    public List<EngagementDto.QuestionResponse> listForCategory(String category) {
        return questionRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(PackageCategory.valueOf(category)).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private static final int MAX_ANSWER_LENGTH = 2000;

    /** Staff overload kept for existing callers (no ownership check, no required-field enforcement). */
    public void submitAnswers(String bookingId, Map<String, String> answers) {
        submitAnswers(bookingId, answers, null, true);
    }

    /**
     * Saves intake answers for a booking.
     *
     * @param actorId the authenticated user's id (never taken from the request body)
     * @param isStaff true for staff roles; clients may only answer for their own booking
     *
     * Validation: only keys belonging to the booking's category are stored; required
     * questions must be answered when the caller is the client; NUMBER/DATE/SELECT
     * answers must match their field type; answers are length-capped.
     */
    public void submitAnswers(String bookingId, Map<String, String> answers, String actorId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        assertCanAccess(booking, actorId, isStaff);

        Map<String, String> safe = answers == null ? Map.of() : answers;
        List<IntakeQuestion> questions = questionRepository
                .findByCategoryAndActiveTrueOrderBySortOrderAsc(booking.getShootCategory());

        for (IntakeQuestion q : questions) {
            String value = safe.get(q.getQuestionKey());
            boolean blank = value == null || value.isBlank();
            if (blank) {
                if (q.isRequired() && !isStaff) {
                    throw new BusinessException("Please answer: " + q.getQuestionText(), "INTAKE_REQUIRED");
                }
                continue;
            }
            validateAnswer(q, value.trim());
        }

        for (IntakeQuestion q : questions) {
            String value = safe.get(q.getQuestionKey());
            if (value == null) continue;
            BookingIntakeAnswer answer = answerRepository.findByBookingIdAndQuestionKey(bookingId, q.getQuestionKey())
                    .orElse(BookingIntakeAnswer.builder().bookingId(bookingId).questionKey(q.getQuestionKey()).build());
            answer.setAnswerValue(value.trim());
            answerRepository.save(answer);
        }
    }

    void validateAnswer(IntakeQuestion q, String value) {
        if (value.length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException("Answer too long for: " + q.getQuestionText(), "INTAKE_INVALID");
        }
        switch (q.getFieldType()) {
            case NUMBER -> {
                try {
                    new java.math.BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException("Please enter a number for: " + q.getQuestionText(), "INTAKE_INVALID");
                }
            }
            case DATE -> {
                try {
                    java.time.LocalDate.parse(value);
                } catch (java.time.format.DateTimeParseException e) {
                    throw new BusinessException("Please enter a valid date for: " + q.getQuestionText(), "INTAKE_INVALID");
                }
            }
            case SELECT -> {
                if (q.getOptions() != null && !q.getOptions().isBlank()) {
                    boolean ok = java.util.Arrays.stream(q.getOptions().split(","))
                            .map(String::trim).anyMatch(o -> o.equalsIgnoreCase(value));
                    if (!ok) {
                        throw new BusinessException("Please choose one of the listed options for: " + q.getQuestionText(), "INTAKE_INVALID");
                    }
                }
            }
            default -> { /* TEXT: length check above is enough */ }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAnswers(String bookingId) {
        return answerRepository.findByBookingId(bookingId).stream()
                .collect(Collectors.toMap(BookingIntakeAnswer::getQuestionKey, a -> a.getAnswerValue() == null ? "" : a.getAnswerValue()));
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAnswers(String bookingId, String actorId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        assertCanAccess(booking, actorId, isStaff);
        return getAnswers(bookingId);
    }

    private void assertCanAccess(Booking booking, String actorId, boolean isStaff) {
        if (isStaff) return;
        if (booking.getClient() == null || actorId == null || !booking.getClient().getId().equals(actorId)) {
            // Same response as a missing booking so ids can't be probed.
            throw new ResourceNotFoundException("Booking", "id", booking.getId());
        }
    }

    private EngagementDto.QuestionResponse toResponse(IntakeQuestion q) {
        return EngagementDto.QuestionResponse.builder()
                .id(q.getId()).category(q.getCategory().name())
                .questionKey(q.getQuestionKey()).questionText(q.getQuestionText())
                .fieldType(q.getFieldType().name()).options(q.getOptions())
                .required(q.isRequired()).sortOrder(q.getSortOrder()).active(q.isActive())
                .build();
    }
}
