package com.cameraman.booking.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.domain.BookingType;
import com.cameraman.booking.dto.BookingDto;
import com.cameraman.booking.repository.BlockedDateRepository;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.booking.repository.TimeSlotRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.notification.service.NotificationPublisher;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.package_mgmt.domain.Package;
import com.cameraman.package_mgmt.repository.PackageRepository;
import com.cameraman.settings.service.StudioSettingsService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the client self-booking rules and reschedule conflict checks. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceSelfBookingTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private AuditService auditService;
    @Mock private StudioSettingsService settingsService;
    @Mock private BlockedDateRepository blockedDateRepository;
    @Mock private PackageRepository packageRepository;

    private BookingService service;
    private final LocalDate future = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, timeSlotRepository, userRepository,
                notificationService, notificationPublisher, auditService, settingsService,
                blockedDateRepository, packageRepository);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BookingDto.CreateRequest baseRequest() {
        return BookingDto.CreateRequest.builder()
                .bookingDate(future).startTime(LocalTime.of(10, 0))
                .type("OFFLINE").shootCategory("FAMILY").packageId("pkg1")
                .totalAmount(BigDecimal.ONE).advanceAmount(new BigDecimal("999999"))
                .photographerId("someone-else").clientId("someone-else").build();
    }

    private Package pkg(boolean active, boolean archived) {
        Package p = new Package();
        p.setName("Signature Portrait");
        p.setBasePrice(new BigDecimal("5000"));
        p.setDurationHours(2);
        p.setActive(active);
        p.setArchived(archived);
        return p;
    }

    private User client(String id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Asha");
        return u;
    }

    @Test
    void clientBooking_priceAndTypeComeFromPackage_notFromRequest() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, false)));
        when(userRepository.findById("c1")).thenReturn(Optional.of(client("c1")));
        BookingDto.CreateRequest req = baseRequest();
        req.setClientId("c1");

        service.createBooking(req, "c1", true);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("5000");
        assertThat(saved.getAdvanceAmount()).isEqualByComparingTo("0");
        assertThat(saved.getBalanceAmount()).isEqualByComparingTo("5000");
        assertThat(saved.getType()).isEqualTo(BookingType.ONLINE);
        assertThat(saved.getPhotographer()).isNull();
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void clientBooking_withoutPackage_isRejected() {
        BookingDto.CreateRequest req = baseRequest();
        req.setPackageId(null);
        assertThatThrownBy(() -> service.createBooking(req, "c1", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clientBooking_archivedPackage_isRejected() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, true)));
        assertThatThrownBy(() -> service.createBooking(baseRequest(), "c1", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clientBooking_inactivePackage_isRejected() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(false, false)));
        assertThatThrownBy(() -> service.createBooking(baseRequest(), "c1", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void booking_inThePast_isRejected() {
        BookingDto.CreateRequest req = baseRequest();
        req.setBookingDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> service.createBooking(req, "staff", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("past");
    }

    @Test
    void booking_endBeforeStart_isRejected() {
        BookingDto.CreateRequest req = baseRequest();
        req.setEndTime(LocalTime.of(9, 0));
        assertThatThrownBy(() -> service.createBooking(req, "staff", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void booking_onBlockedDate_isRejected() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, false)));
        when(blockedDateRepository.existsByDate(future)).thenReturn(true);
        assertThatThrownBy(() -> service.createBooking(baseRequest(), "c1", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void booking_overlappingExistingSlot_isRejected() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, false)));
        Booking existing = Booking.builder().id("x").status(BookingStatus.CONFIRMED)
                .bookingDate(future).startTime(LocalTime.of(11, 0)).endTime(LocalTime.of(12, 0)).build();
        when(bookingRepository.findByBookingDate(future)).thenReturn(List.of(existing));

        // package is 2h starting 10:00 -> 10:00-12:00 overlaps 11:00-12:00
        assertThatThrownBy(() -> service.createBooking(baseRequest(), "c1", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void booking_adjacentSlot_isAllowed() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, false)));
        when(userRepository.findById("c1")).thenReturn(Optional.of(client("c1")));
        Booking existing = Booking.builder().id("x").status(BookingStatus.CONFIRMED)
                .bookingDate(future).startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0)).build();
        when(bookingRepository.findByBookingDate(future)).thenReturn(List.of(existing));
        BookingDto.CreateRequest req = baseRequest();
        req.setClientId("c1");

        assertThat(service.createBooking(req, "c1", true)).isNotNull();
    }

    @Test
    void booking_cancelledBookingDoesNotBlockSlot() {
        when(packageRepository.findById("pkg1")).thenReturn(Optional.of(pkg(true, false)));
        when(userRepository.findById("c1")).thenReturn(Optional.of(client("c1")));
        Booking cancelled = Booking.builder().id("x").status(BookingStatus.CANCELLED)
                .bookingDate(future).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0)).build();
        when(bookingRepository.findByBookingDate(future)).thenReturn(List.of(cancelled));
        BookingDto.CreateRequest req = baseRequest();
        req.setClientId("c1");

        assertThat(service.createBooking(req, "c1", true)).isNotNull();
    }

    @Test
    void reschedule_ontoAnotherBookingsSlot_isRejected() {
        Booking mine = Booking.builder().id("mine").status(BookingStatus.CONFIRMED)
                .bookingDate(future).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build();
        Booking other = Booking.builder().id("other").status(BookingStatus.CONFIRMED)
                .bookingDate(future.plusDays(1)).startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(15, 0)).build();
        when(bookingRepository.findById("mine")).thenReturn(Optional.of(mine));
        when(bookingRepository.findByBookingDate(future.plusDays(1))).thenReturn(List.of(other));

        BookingDto.RescheduleRequest req = BookingDto.RescheduleRequest.builder()
                .newDate(future.plusDays(1)).newStartTime(LocalTime.of(14, 0))
                .newEndTime(LocalTime.of(15, 0)).reason("conflict").build();

        assertThatThrownBy(() -> service.reschedule("mine", req, "staff", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void reschedule_withinSameBooking_ignoresItself() {
        Booking mine = Booking.builder().id("mine").status(BookingStatus.CONFIRMED).type(BookingType.ONLINE)
                .bookingDate(future).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build();
        when(bookingRepository.findById("mine")).thenReturn(Optional.of(mine));
        when(bookingRepository.findByBookingDate(future)).thenReturn(List.of(mine));

        BookingDto.RescheduleRequest req = BookingDto.RescheduleRequest.builder()
                .newDate(future).newStartTime(LocalTime.of(9, 30))
                .newEndTime(LocalTime.of(10, 30)).reason("shift").build();

        BookingDto.BookingResponse res = service.reschedule("mine", req, "staff", false);
        assertThat(res.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(res.getRescheduledCount()).isEqualTo(1);
    }

    @Test
    void clientCannotRescheduleSomeoneElsesBooking() {
        Booking theirs = Booking.builder().id("t").status(BookingStatus.CONFIRMED)
                .client(client("other-user")).bookingDate(future).startTime(LocalTime.of(9, 0)).build();
        when(bookingRepository.findById("t")).thenReturn(Optional.of(theirs));
        BookingDto.RescheduleRequest req = BookingDto.RescheduleRequest.builder()
                .newDate(future.plusDays(2)).newStartTime(LocalTime.of(9, 0)).reason("x").build();

        assertThatThrownBy(() -> service.reschedule("t", req, "me", true))
                .isInstanceOf(BusinessException.class);
    }
}
