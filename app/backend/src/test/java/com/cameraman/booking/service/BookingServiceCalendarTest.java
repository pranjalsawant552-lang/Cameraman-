package com.cameraman.booking.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.domain.BookingType;
import com.cameraman.booking.dto.BookingDto;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.booking.repository.BlockedDateRepository;
import com.cameraman.booking.repository.TimeSlotRepository;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.audit.service.AuditService;
import com.cameraman.settings.service.StudioSettingsService;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The calendar previously only returned ONLINE bookings, which meant an offline/walk-in
 * booking made by reception was completely invisible on the staff calendar — two people
 * could double-book a slot without either seeing the other's entry, despite the app
 * already having conflict-detection logic that assumed a complete picture. This test
 * exists specifically to catch that filter if it's ever silently reintroduced (e.g. by a
 * future change that "cleans up" the query without knowing why the filter was removed).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceCalendarTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    // Added to BookingService before this test file was last touched — the constructor
    // call below was never updated to match, so this test was already failing to compile
    // independent of anything else. Unused by the test below.
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private AuditService auditService;
    @Mock private StudioSettingsService settingsService;
    @Mock private BlockedDateRepository blockedDateRepository;
    @Mock private com.cameraman.package_mgmt.repository.PackageRepository packageRepository;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, timeSlotRepository, userRepository,
                notificationService, notificationPublisher, auditService, settingsService, blockedDateRepository, packageRepository);
    }

    @Test
    void getCalendarEvents_includesBothOnlineAndOfflineBookings() {
        Booking onlineBooking = Booking.builder()
                .id("b-online").bookingNumber("BK001").clientName("Priya Sharma")
                .type(BookingType.ONLINE).status(BookingStatus.CONFIRMED)
                .bookingDate(LocalDate.now()).startTime(LocalTime.of(10, 0))
                .build();
        Booking offlineBooking = Booking.builder()
                .id("b-offline").bookingNumber("BK002").clientName("Rahul Verma")
                .type(BookingType.OFFLINE).status(BookingStatus.CONFIRMED)
                .bookingDate(LocalDate.now()).startTime(LocalTime.of(11, 0))
                .build();

        when(bookingRepository.findByBookingDateBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(onlineBooking, offlineBooking));

        List<BookingDto.CalendarEvent> events = service.getCalendarEvents(LocalDate.now(), LocalDate.now());

        assertThat(events).hasSize(2);
        assertThat(events).extracting(BookingDto.CalendarEvent::getType)
                .containsExactlyInAnyOrder("ONLINE", "OFFLINE");
    }

    @Test
    void getCalendarEvents_offlineOnly_stillReturned() {
        // The specific regression case: before the fix, this would have returned an empty list.
        Booking offlineBooking = Booking.builder()
                .id("b-offline").bookingNumber("BK003").clientName("Walk-in Client")
                .type(BookingType.OFFLINE).status(BookingStatus.PENDING)
                .bookingDate(LocalDate.now()).startTime(LocalTime.of(14, 0))
                .build();

        when(bookingRepository.findByBookingDateBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(offlineBooking));

        List<BookingDto.CalendarEvent> events = service.getCalendarEvents(LocalDate.now(), LocalDate.now());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo("OFFLINE");
    }
}
