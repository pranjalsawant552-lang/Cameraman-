package com.cameraman.booking.scheduler;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    /**
     * Run every day at 9 AM to send reminders for tomorrow's bookings
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Booking> bookings = bookingRepository.findBookingsForTomorrowReminder(tomorrow);

        log.info("Sending reminders for {} bookings on {}", bookings.size(), tomorrow);

        for (Booking booking : bookings) {
            try {
                notificationService.sendBookingConfirmation(booking);
                booking.setReminderSent(true);
                bookingRepository.save(booking);
            } catch (Exception e) {
                log.error("Failed to send reminder for booking {}: {}", booking.getBookingNumber(), e.getMessage());
            }
        }
    }

    /**
     * Run every day at midnight to mark overdue bookings
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueBookings() {
        log.info("Checking for overdue bookings");
        // Mark confirmed bookings from past 3+ days that haven't been completed
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        bookingRepository.findByBookingDateBetween(LocalDate.now().minusYears(1), threeDaysAgo)
                .stream()
                .filter(b -> b.getStatus().name().equals("CONFIRMED"))
                .forEach(b -> {
                    log.warn("Overdue booking detected: {}", b.getBookingNumber());
                });
    }
}
