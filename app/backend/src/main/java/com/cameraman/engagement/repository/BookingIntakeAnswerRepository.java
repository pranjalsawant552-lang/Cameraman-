package com.cameraman.engagement.repository;

import com.cameraman.engagement.domain.BookingIntakeAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingIntakeAnswerRepository extends JpaRepository<BookingIntakeAnswer, String> {
    List<BookingIntakeAnswer> findByBookingId(String bookingId);
    Optional<BookingIntakeAnswer> findByBookingIdAndQuestionKey(String bookingId, String questionKey);
    List<BookingIntakeAnswer> findByQuestionKey(String questionKey);
}
