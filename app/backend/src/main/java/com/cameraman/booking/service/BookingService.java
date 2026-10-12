package com.cameraman.booking.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.booking.domain.*;
import com.cameraman.booking.dto.BookingDto;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.booking.repository.TimeSlotRepository;
import com.cameraman.booking.repository.BlockedDateRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.notification.service.NotificationService;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.cameraman.notification.service.NotificationPublisher notificationPublisher;
    private final AuditService auditService;
    private final com.cameraman.settings.service.StudioSettingsService settingsService;
    private final BlockedDateRepository blockedDateRepository;
    private final com.cameraman.package_mgmt.repository.PackageRepository packageRepository;
    private final com.cameraman.billing.service.BillingService billingService;
    private final com.cameraman.notification.service.EmailService emailService;
    private final com.cameraman.whatsapp.service.WhatsAppService whatsAppService;

    private static final List<String> STAFF_NOTIFY_ROLES = List.of("ADMIN", "SUPER_ADMIN", "MANAGER", "RECEPTIONIST");

    private static final AtomicLong bookingCounter = new AtomicLong(1000);

    public BookingDto.BookingResponse createBooking(BookingDto.CreateRequest request, String createdBy) {
        return createBooking(request, createdBy, false);
    }

    /**
     * @param isClientActor true when a CLIENT is booking for themself. In that case nothing
     *                      money-related or identity-related from the request body is trusted:
     *                      the package must exist and be active, the price comes from the
     *                      package, no advance can be claimed, and the booking is ONLINE.
     */
    public BookingDto.BookingResponse createBooking(BookingDto.CreateRequest request, String createdBy, boolean isClientActor) {
        if (request.getBookingDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Bookings cannot be made for a past date.", "DATE_IN_PAST");
        }
        if (request.getEndTime() != null && !request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("End time must be after the start time.", "INVALID_TIME_RANGE");
        }
        if (isClientActor) {
            if (request.getPackageId() == null || request.getPackageId().isBlank()) {
                throw new BusinessException("Please choose a package.", "PACKAGE_REQUIRED");
            }
            com.cameraman.package_mgmt.domain.Package pkg = packageRepository.findById(request.getPackageId())
                    .filter(p -> p.isActive() && !p.isArchived())
                    .orElseThrow(() -> new BusinessException("This package is no longer available.", "PACKAGE_UNAVAILABLE"));
            request.setPackageName(pkg.getName());
            request.setTotalAmount(pkg.getEffectivePrice());
            request.setAdvanceAmount(BigDecimal.ZERO);
            request.setType("ONLINE");
            request.setPhotographerId(null);
            if (request.getEndTime() == null && pkg.getDurationHours() != null && pkg.getDurationHours() > 0) {
                request.setEndTime(request.getStartTime().plusHours(pkg.getDurationHours()));
            }
        }
        if (blockedDateRepository.existsByDate(request.getBookingDate())) {
            throw new BusinessException(
                    "The studio is closed on " + request.getBookingDate() + ". Please choose a different date.",
                    "DATE_BLOCKED");
        }

        // Lock the slot: no one else may hold an active (non-cancelled) booking that
        // overlaps this date/time range, regardless of photographer.
        LocalTime newStart = request.getStartTime();
        LocalTime newEnd = request.getEndTime() != null ? request.getEndTime() : newStart.plusHours(1);

        List<Booking> sameDayBookings = bookingRepository.findByBookingDate(request.getBookingDate());
        List<Booking> activeSameDayBookings = sameDayBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        boolean slotTaken = activeSameDayBookings.stream().anyMatch(b -> overlaps(b, newStart, newEnd));
        if (slotTaken) {
            throw new BusinessException(
                    "This date/time slot is already booked. Please choose a different slot.", "SLOT_TAKEN");
        }

        // Validate photographer availability: only conflicts if the SAME photographer
        // already has an overlapping booking that day, not merely any booking that day
        // (a photographer can legitimately have several non-overlapping shoots in a day).
        if (request.getPhotographerId() != null) {
            boolean conflict = activeSameDayBookings.stream()
                    .filter(b -> b.getPhotographer() != null
                            && request.getPhotographerId().equals(b.getPhotographer().getId()))
                    .anyMatch(b -> overlaps(b, newStart, newEnd));
            if (conflict) {
                throw new BusinessException("Photographer already has a booking in this time slot", "PHOTOGRAPHER_UNAVAILABLE");
            }
        }

        User client = null;
        if (request.getClientId() != null && !request.getClientId().isBlank()) {
            client = userRepository.findById(request.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client", "id", request.getClientId()));
        }

        User photographer = null;
        if (request.getPhotographerId() != null && !request.getPhotographerId().isBlank()) {
            photographer = userRepository.findById(request.getPhotographerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Photographer", "id", request.getPhotographerId()));
        }

        com.cameraman.booking.domain.LocationType locationType = com.cameraman.booking.domain.LocationType.parse(request.getLocationType());
        if (locationType == com.cameraman.booking.domain.LocationType.OUTDOOR
                && (request.getVenue() == null || request.getVenue().isBlank())) {
            throw new BusinessException("Please enter the venue/location for an outdoor booking.", "VENUE_REQUIRED");
        }

        BigDecimal totalAmount = request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal advance = request.getAdvanceAmount() != null ? request.getAdvanceAmount() : BigDecimal.ZERO;
        BigDecimal balance = totalAmount.subtract(advance);

        Booking booking = Booking.builder()
                .bookingNumber(generateBookingNumber())
                .client(client)
                .clientName(client != null ? client.getFullName() : request.getClientName())
                .clientMobile(client != null ? client.getMobile() : request.getClientMobile())
                .clientEmail(client != null ? client.getEmail() : request.getClientEmail())
                .bookingDate(request.getBookingDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .packageId(request.getPackageId())
                .packageName(request.getPackageName())
                .type(BookingType.valueOf(request.getType()))
                .shootCategory(request.getShootCategory() != null
                        ? com.cameraman.package_mgmt.domain.PackageCategory.valueOf(request.getShootCategory())
                        : com.cameraman.package_mgmt.domain.PackageCategory.OTHER)
                // Staff-entered bookings are confirmed from the start; a client's own online request
                // stays PENDING until staff confirm it or the first payment arrives.
                .status(isClientActor ? BookingStatus.PENDING : BookingStatus.CONFIRMED)
                .photographer(photographer)
                .venue(request.getVenue())
                .locationType(locationType)
                .notes(request.getNotes())
                .advanceAmount(advance)
                .totalAmount(totalAmount)
                .balanceAmount(balance)
                .marketingOptIn(request.getMarketingOptIn() == null || request.getMarketingOptIn())
                .build();

        // Save with a small retry loop: the booking-number counter is only
        // process-local, so on the rare chance two instances/threads collide on
        // the same number we regenerate and retry rather than failing the whole
        // request. The DB-level slot-lock index is checked separately so we can
        // tell the two failure modes apart and report the right message.
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                booking = bookingRepository.saveAndFlush(booking);
                break;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                String msg = String.valueOf(e.getMostSpecificCause() != null
                        ? e.getMostSpecificCause().getMessage() : e.getMessage());
                if (msg.contains("idx_booking_slot_lock")) {
                    // A concurrent request grabbed this exact slot first (DB unique index guard)
                    throw new BusinessException(
                            "This date/time slot was just booked by someone else. Please choose a different slot.",
                            "SLOT_TAKEN");
                }
                if (attempts >= 3) {
                    throw e;
                }
                // Booking-number collision (e.g. two instances racing on the counter) — regenerate and retry.
                booking.setBookingNumber(generateBookingNumber());
            }
        }

        // Send confirmation notification
        try {
            notificationService.sendBookingConfirmation(booking);
        } catch (Exception e) {
            log.error("Failed to send booking confirmation: {}", e.getMessage());
        }

        notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES, "BOOKING_CREATED",
                "New booking: " + booking.getBookingNumber(),
                booking.getClientName() + " booked " + (booking.getPackageName() != null ? booking.getPackageName() : booking.getShootCategory().name())
                        + " on " + booking.getBookingDate(),
                "Booking", booking.getId());

        auditService.log("BOOKING_CREATE", "Booking", booking.getId(), null, booking.getBookingNumber(), createdBy);

        // Every booking gets an invoice. It runs AFTER this transaction commits and in its own transaction
        // (see BillingService.createInvoiceForBooking), so an invoicing failure can never roll back or
        // fail the booking itself; staff can still use "Generate invoice" on the booking.
        final String newBookingId = booking.getId();
        final String invoiceCreatedBy = createdBy;
        Runnable invoiceTask = () -> {
            try {
                billingService.createInvoiceForBooking(newBookingId, invoiceCreatedBy);
            } catch (Exception e) {
                log.error("Auto-invoice failed for booking {}: {}", newBookingId, e.getMessage(), e);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { invoiceTask.run(); }
                    });
        } else {
            invoiceTask.run();
        }

        return toResponse(booking);
    }

    public BookingDto.BookingResponse updateBooking(String id, BookingDto.UpdateRequest request, String updatedBy) {
        Booking booking = findById(id);
        String oldStatus = booking.getStatus().name();

        // Same rules as creating a booking: no past date, no closed day, no clash with another booking.
        if (request.getBookingDate() != null || request.getStartTime() != null || request.getEndTime() != null) {
            final LocalDate newDate = request.getBookingDate() != null ? request.getBookingDate() : booking.getBookingDate();
            final LocalTime newStart = request.getStartTime() != null ? request.getStartTime() : booking.getStartTime();
            final LocalTime requestedEnd = request.getEndTime() != null ? request.getEndTime() : booking.getEndTime();
            if (requestedEnd != null && !requestedEnd.isAfter(newStart)) {
                throw new BusinessException("End time must be after the start time.", "INVALID_TIME_RANGE");
            }
            boolean dateChanged = !newDate.equals(booking.getBookingDate());
            if (dateChanged && newDate.isBefore(LocalDate.now())) {
                throw new BusinessException("Bookings cannot be moved to a past date.", "DATE_IN_PAST");
            }
            if (dateChanged && blockedDateRepository.existsByDate(newDate)) {
                throw new BusinessException("The studio is closed on " + newDate + ". Please choose a different date.", "DATE_BLOCKED");
            }
            final LocalTime newEnd = requestedEnd != null ? requestedEnd : newStart.plusHours(1);
            final String selfId = booking.getId();
            boolean clash = bookingRepository.findByBookingDate(newDate).stream()
                    .filter(other -> !other.getId().equals(selfId))
                    .filter(other -> other.getStatus() != BookingStatus.CANCELLED)
                    .anyMatch(other -> overlaps(other, newStart, newEnd));
            if (clash) {
                throw new BusinessException("That date/time slot is already booked. Please choose a different slot.", "SLOT_TAKEN");
            }
        }
        if (request.getBookingDate() != null) booking.setBookingDate(request.getBookingDate());
        if (request.getStartTime() != null) booking.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) booking.setEndTime(request.getEndTime());
        if (request.getVenue() != null) booking.setVenue(request.getVenue());
        if (request.getLocationType() != null) booking.setLocationType(com.cameraman.booking.domain.LocationType.parse(request.getLocationType()));
        if (booking.getLocationType() == com.cameraman.booking.domain.LocationType.OUTDOOR
                && (booking.getVenue() == null || booking.getVenue().isBlank())) {
            throw new BusinessException("Please enter the venue/location for an outdoor booking.", "VENUE_REQUIRED");
        }
        if (request.getNotes() != null) booking.setNotes(request.getNotes());
        if (request.getShootCategory() != null) {
            booking.setShootCategory(com.cameraman.package_mgmt.domain.PackageCategory.valueOf(request.getShootCategory()));
        }
        if (request.getTotalAmount() != null) {
            boolean totalChanged = booking.getTotalAmount() == null
                    || request.getTotalAmount().compareTo(booking.getTotalAmount()) != 0;
            booking.setTotalAmount(request.getTotalAmount());
            booking.setBalanceAmount(request.getTotalAmount().subtract(booking.getAdvanceAmount()));
            // Keep the invoice in step with the new total (refused if it would drop below what was paid).
            if (totalChanged) billingService.resyncInvoiceTotal(booking.getId(), request.getTotalAmount());
        }
        if (request.getStatus() != null) {
            BookingStatus requested;
            try {
                requested = BookingStatus.valueOf(request.getStatus().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Unknown booking status: " + request.getStatus(), "INVALID_STATUS");
            }
            if (requested == BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.CANCELLED) {
                throw new BusinessException("Use Cancel to cancel a booking, so the client is told and the invoice is handled.", "USE_CANCEL_ACTION");
            }
            booking.setStatus(requested);
        }
        if (request.getPhotographerId() != null && !request.getPhotographerId().isBlank()) {
            User photographer = userRepository.findById(request.getPhotographerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Photographer", "id", request.getPhotographerId()));
            booking.setPhotographer(photographer);
        }

        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_UPDATE", "Booking", booking.getId(), oldStatus, booking.getStatus().name(), updatedBy);

        return toResponse(booking);
    }

    public BookingDto.BookingResponse reschedule(String id, BookingDto.RescheduleRequest request, String userId) {
        return reschedule(id, request, userId, false);
    }

    /**
     * @param isClientActor true when the caller is the CLIENT themself (self-service),
     *                       as opposed to staff acting on their behalf. Clients are
     *                       restricted to their own bookings and to the configured
     *                       reschedule policy window; staff bypass both.
     */
    public BookingDto.BookingResponse reschedule(String id, BookingDto.RescheduleRequest request, String userId, boolean isClientActor) {
        Booking booking = findById(id);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("Cannot reschedule a cancelled booking", "BOOKING_CANCELLED");
        }
        if (blockedDateRepository.existsByDate(request.getNewDate())) {
            throw new BusinessException(
                    "The studio is closed on " + request.getNewDate() + ". Please choose a different date.",
                    "DATE_BLOCKED");
        }

        LocalTime newStart = request.getNewStartTime();
        if (request.getNewDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Bookings cannot be moved to a past date.", "DATE_IN_PAST");
        }
        // Keep the original duration when no new end time is given (instead of blanking the end time).
        final LocalTime newEnd = request.getNewEndTime() != null ? request.getNewEndTime()
                : (booking.getEndTime() != null
                        ? newStart.plus(java.time.Duration.between(booking.getStartTime(), booking.getEndTime()))
                        : newStart.plusHours(1));
        if (!newEnd.isAfter(newStart)) {
            throw new BusinessException("End time must be after the start time.", "INVALID_TIME_RANGE");
        }
        final String selfId = booking.getId();
        boolean taken = bookingRepository.findByBookingDate(request.getNewDate()).stream()
                .filter(b -> !b.getId().equals(selfId))
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .anyMatch(b -> overlaps(b, newStart, newEnd));
        if (taken) {
            throw new BusinessException(
                    "That date/time slot is already booked. Please choose a different slot.", "SLOT_TAKEN");
        }

        if (isClientActor) {
            if (booking.getClient() == null || !booking.getClient().getId().equals(userId)) {
                throw new BusinessException("You can only reschedule your own bookings", "NOT_YOUR_BOOKING");
            }
            java.time.LocalDateTime bookingStart = java.time.LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
            int windowHours = settingsService.getRescheduleWindowHours();
            if (java.time.LocalDateTime.now().isAfter(bookingStart.minusHours(windowHours))) {
                throw new BusinessException(
                        "This booking is within " + windowHours + " hours of the shoot and can no longer be self-rescheduled. Please contact us directly.",
                        "OUTSIDE_RESCHEDULE_WINDOW");
            }
        }

        booking.setRescheduledFrom(booking.getBookingDate());
        booking.setBookingDate(request.getNewDate());
        booking.setStartTime(request.getNewStartTime());
        booking.setEndTime(newEnd);
        booking.setStatus(BookingStatus.RESCHEDULED);
        booking.setRescheduledCount(booking.getRescheduledCount() + 1);

        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Another request took the same slot between our check and the write
            // (idx_booking_slot_lock is the race-safe guard).
            throw new BusinessException(
                    "That date/time slot was just booked by someone else. Please choose a different slot.", "SLOT_TAKEN");
        }

        try {
            notificationService.sendBookingRescheduled(booking, request.getReason());
        } catch (Exception e) {
            log.error("Failed to send reschedule notification: {}", e.getMessage());
        }

        auditService.log("BOOKING_RESCHEDULE", "Booking", booking.getId(), null, request.getNewDate().toString(), userId);
        return toResponse(booking);
    }

    public BookingDto.BookingResponse cancelBooking(String id, BookingDto.CancelRequest request, String userId) {
        return cancelBooking(id, request, userId, false);
    }

    public BookingDto.BookingResponse cancelBooking(String id, BookingDto.CancelRequest request, String userId, boolean isClientActor) {
        Booking booking = findById(id);

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel a completed booking", "BOOKING_COMPLETED");
        }

        if (isClientActor) {
            if (booking.getClient() == null || !booking.getClient().getId().equals(userId)) {
                throw new BusinessException("You can only cancel your own bookings", "NOT_YOUR_BOOKING");
            }
            java.time.LocalDateTime bookingStart = java.time.LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
            int windowHours = settingsService.getCancellationWindowHours();
            if (java.time.LocalDateTime.now().isAfter(bookingStart.minusHours(windowHours))) {
                throw new BusinessException(
                        "This booking is within " + windowHours + " hours of the shoot and can no longer be self-cancelled. Please contact us directly.",
                        "OUTSIDE_CANCELLATION_WINDOW");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledReason(request.getReason());
        booking = bookingRepository.save(booking);

        try {
            notificationService.sendBookingCancelled(booking);
        } catch (Exception e) {
            log.error("Failed to send cancellation notification: {}", e.getMessage());
        }

        notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES, "BOOKING_CANCELLED",
                "Booking cancelled: " + booking.getBookingNumber(),
                booking.getClientName() + "'s booking on " + booking.getBookingDate() + " was cancelled. Reason: " + request.getReason(),
                "Booking", booking.getId());

        // An unpaid invoice is cancelled with the booking. If money was already received, tell staff
        // to decide (refund, or keep and cancel the invoice) instead of leaving a phantom balance.
        boolean moneyHeld = false;
        try {
            moneyHeld = billingService.cancelInvoiceForSource(booking.getId(), request.getReason());
        } catch (Exception e) {
            log.error("Could not cancel invoice for booking {}: {}", booking.getBookingNumber(), e.getMessage(), e);
        }
        if (moneyHeld) {
            notificationPublisher.notifyRoles(STAFF_NOTIFY_ROLES, "BOOKING_CANCELLED",
                    "Refund decision needed: " + booking.getBookingNumber(),
                    "An advance was received on this cancelled booking. Refund it from Payments, or keep it, then cancel the invoice.",
                    "Booking", booking.getId());
        }

        auditService.log("BOOKING_CANCEL", "Booking", booking.getId(), null, request.getReason(), userId);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingDto.BookingResponse getById(String id) {
        return toResponse(findById(id));
    }

    /**
     * Advances (or explicitly re-sets) a booking's order-pipeline stage.
     * Records a history row and notifies the client. Staff can move the
     * stage in any direction (e.g. correcting a mistake) — this
     * deliberately does not enforce strict forward-only transitions, since
     * real workflows sometimes need a step reopened (e.g. a revision
     * request bounces "Album Designing" back from "Printing").
     */
    public BookingDto.BookingResponse updateOrderStage(String id, BookingDto.UpdateStageRequest request, String userId) {
        Booking booking = findById(id);
        OrderStage newStage;
        try {
            newStage = OrderStage.valueOf(request.getStage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown order stage: " + request.getStage(), "INVALID_ORDER_STAGE");
        }

        booking.setOrderStage(newStage);
        // Keep the booking status in step with the job: shoot done (or later) = completed; paying the
        // advance (or later) confirms a pending booking; stepping back before "shoot done" reopens it.
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            if (newStage.ordinal() >= OrderStage.SHOOT_DONE.ordinal()) {
                booking.setStatus(BookingStatus.COMPLETED);
            } else if (booking.getStatus() == BookingStatus.COMPLETED) {
                booking.setStatus(BookingStatus.CONFIRMED);
            } else if (booking.getStatus() == BookingStatus.PENDING && newStage.ordinal() >= OrderStage.ADVANCE_PAID.ordinal()) {
                booking.setStatus(BookingStatus.CONFIRMED);
            }
        }
        if (newStage == OrderStage.DELIVERED) {
            booking.setDeliveredAt(java.time.LocalDateTime.now());
            if (booking.getPickedUpAt() == null && booking.getReadyForPickupAt() != null) booking.setPickedUpAt(java.time.LocalDateTime.now());
        }

        String changedByName = userRepository.findById(userId).map(User::getFullName).orElse(null);
        booking.getStageHistory().add(OrderStageHistory.builder()
                .booking(booking)
                .stage(newStage)
                .note(request.getNote())
                .changedByName(changedByName)
                .build());

        booking = bookingRepository.save(booking);

        try {
            notificationService.sendOrderStageUpdate(booking, newStage);
        } catch (Exception e) {
            log.error("Failed to send order stage update notification: {}", e.getMessage());
        }

        auditService.log("BOOKING_STAGE_UPDATE", "Booking", booking.getId(), null, newStage.name(), userId);
        return toResponse(booking);
    }

    /** Staff confirm a pending (or rescheduled) booking. */
    public BookingDto.BookingResponse confirmBooking(String id, String userId) {
        Booking booking = findById(id);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.RESCHEDULED) {
            throw new BusinessException("Only a pending or rescheduled booking can be confirmed.", "INVALID_STATUS");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_CONFIRM", "Booking", booking.getId(), null, booking.getBookingNumber(), userId);
        return toResponse(booking);
    }

    /** Sets/updates courier provider + tracking number for a dispatched order. */
    public BookingDto.BookingResponse updateCourierInfo(String id, BookingDto.UpdateCourierRequest request, String userId) {
        Booking booking = findById(id);
        booking.setCourierProvider(request.getCourierProvider());
        booking.setCourierTrackingNumber(request.getCourierTrackingNumber());
        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_COURIER_UPDATE", "Booking", booking.getId(), null, request.getCourierTrackingNumber(), userId);
        return toResponse(booking);
    }

    /** Assigns (or reassigns) the editor responsible for post-production on this booking. */
    public BookingDto.BookingResponse assignEditor(String id, BookingDto.AssignEditorRequest request, String userId) {
        Booking booking = findById(id);
        User editor = userRepository.findById(request.getEditorId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getEditorId()));
        booking.setEditor(editor);
        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_EDITOR_ASSIGN", "Booking", booking.getId(), null, editor.getId(), userId);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookingDto.BookingResponse> getAllBookings(String search, String status, Pageable pageable) {
        return getAllBookings(search, status, null, null, pageable);
    }

    /** Filters combine (AND): free-text search, status, INDOOR/OUTDOOR, and pickup state
     *  (AWAITING_PICKUP = marked ready but not collected, PICKED_UP). */
    @Transactional(readOnly = true)
    public PagedResponse<BookingDto.BookingResponse> getAllBookings(String search, String status, String locationType,
                                                                    String pickup, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Booking> spec = (root, q, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("bookingNumber")), like),
                    cb.like(cb.lower(root.get("clientName")), like),
                    cb.like(root.get("clientMobile"), "%" + search.trim() + "%"),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("clientEmail"), "")), like)));
        }
        if (status != null && !status.isBlank()) {
            BookingStatus st = BookingStatus.valueOf(status.trim().toUpperCase());
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), st));
        }
        if (locationType != null && !locationType.isBlank()) {
            var lt = com.cameraman.booking.domain.LocationType.parse(locationType);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("locationType"), lt));
        }
        if (pickup != null && !pickup.isBlank()) {
            String p = pickup.trim().toUpperCase();
            if ("AWAITING_PICKUP".equals(p)) {
                spec = spec.and((root, q, cb) -> cb.and(cb.isNotNull(root.get("readyForPickupAt")), cb.isNull(root.get("pickedUpAt"))));
            } else if ("PICKED_UP".equals(p)) {
                spec = spec.and((root, q, cb) -> cb.isNotNull(root.get("pickedUpAt")));
            } else {
                throw new BusinessException("pickup must be AWAITING_PICKUP or PICKED_UP", "INVALID_PICKUP_FILTER");
            }
        }
        return PagedResponse.of(bookingRepository.findAll(spec, pageable).map(this::toResponse));
    }

    // ───────── Pickup tracking (ready photos the client has not collected) ─────────

    public BookingDto.BookingResponse markReadyForPickup(String id, String userId) {
        Booking booking = findById(id);
        if (booking.getPickedUpAt() != null) {
            throw new BusinessException("This order has already been picked up.", "ALREADY_PICKED_UP");
        }
        if (booking.getReadyForPickupAt() == null) booking.setReadyForPickupAt(java.time.LocalDateTime.now());
        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_READY_FOR_PICKUP", "Booking", booking.getId(), null, booking.getBookingNumber(), userId);
        return toResponse(booking);
    }

    public BookingDto.BookingResponse markPickedUp(String id, String userId) {
        Booking booking = findById(id);
        booking.setPickedUpAt(java.time.LocalDateTime.now());
        if (booking.getReadyForPickupAt() == null) booking.setReadyForPickupAt(booking.getPickedUpAt());
        booking = bookingRepository.save(booking);
        auditService.log("BOOKING_PICKED_UP", "Booking", booking.getId(), null, booking.getBookingNumber(), userId);
        return toResponse(booking);
    }

    /** Sends the pickup reminder over WhatsApp and/or email and reports truthfully what went out. */
    public BookingDto.PickupReminderResult sendPickupReminder(String id, String userId) {
        Booking booking = findById(id);
        if (booking.getReadyForPickupAt() == null) {
            throw new BusinessException("Mark the order as ready for pickup before sending a reminder.", "NOT_READY_FOR_PICKUP");
        }
        if (booking.getPickedUpAt() != null) {
            throw new BusinessException("This order has already been picked up.", "ALREADY_PICKED_UP");
        }
        boolean hasMobile = booking.getClientMobile() != null && !booking.getClientMobile().isBlank();
        boolean hasEmail = booking.getClientEmail() != null && !booking.getClientEmail().isBlank();
        if (!hasMobile && !hasEmail) {
            throw new BusinessException("This client has no mobile number or email on file.", "NO_CONTACT");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(booking.getReadyForPickupAt(), java.time.LocalDateTime.now());
        String studio = settingsService.getStudioSettings().getStudioName();
        boolean whatsapp = hasMobile && whatsAppService.sendPickupReminder(
                booking.getClientMobile(), booking.getClientName(), booking.getBookingNumber(), studio, days);
        boolean email = hasEmail && emailService.sendPickupReminderEmail(
                booking.getClientEmail(), booking.getClientName(), booking.getBookingNumber(), days);
        if (!whatsapp && !email) {
            throw new BusinessException("The reminder could not be delivered. WhatsApp/email may not be configured, or the contact details are invalid.", "REMINDER_NOT_SENT");
        }
        booking.setPickupReminderCount(booking.getPickupReminderCount() + 1);
        booking.setLastPickupReminderAt(java.time.LocalDateTime.now());
        bookingRepository.save(booking);
        auditService.log("BOOKING_PICKUP_REMINDER", "Booking", booking.getId(), null, booking.getBookingNumber(), userId);
        return BookingDto.PickupReminderResult.builder()
                .whatsappSent(whatsapp).emailSent(email)
                .reminderCount(booking.getPickupReminderCount())
                .booking(toResponse(booking)).build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookingDto.BookingResponse> getClientBookings(String clientId, Pageable pageable) {
        return PagedResponse.of(bookingRepository.findByClientId(clientId, pageable).map(this::toResponse));
    }

    /** Only ONLINE (client self-booked) bookings appear on the calendar — those are
     *  the ones where visualizing slot availability actually matters. Staff manually
     *  entering an OFFLINE/WALK_IN booking already know the date/time they're
     *  coordinating with the client directly; cluttering the calendar with those
     *  doesn't add value and was making it harder to see real online-booking load. */
    @Transactional(readOnly = true)
    public List<BookingDto.CalendarEvent> getCalendarEvents(LocalDate from, LocalDate to) {
        // Previously ONLINE-only, meaning staff had no single view of their actual day —
        // a walk-in booked directly by reception was invisible on this calendar, so two
        // staff members relying on it could genuinely double-book a slot without seeing
        // each other's offline bookings. Now shows both; the frontend distinguishes them
        // visually (see CalendarComponent) rather than hiding one type entirely.
        return bookingRepository.findByBookingDateBetween(from, to).stream()
                .map(this::toCalendarEvent)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingDto.SlotAvailability> checkAvailability(BookingDto.AvailabilityRequest request) {
        java.util.Optional<BlockedDate> blocked = blockedDateRepository.findByDate(request.getDate());
        if (blocked.isPresent()) {
            String reason = blocked.get().getReason() != null ? blocked.get().getReason() : "Studio closed";
            List<BookingDto.SlotAvailability> closedSlots = new java.util.ArrayList<>();
            LocalTime s = LocalTime.of(9, 0);
            while (s.isBefore(LocalTime.of(19, 0))) {
                closedSlots.add(BookingDto.SlotAvailability.builder()
                        .startTime(s).endTime(s.plusHours(1)).available(false).closedReason(reason).build());
                s = s.plusHours(1);
            }
            return closedSlots;
        }

        List<Booking> existingBookings = bookingRepository.findByBookingDate(request.getDate())
                .stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        // Generate standard slots (9AM - 7PM, 1-hour slots)
        List<BookingDto.SlotAvailability> slots = new java.util.ArrayList<>();
        LocalTime slotStart = LocalTime.of(9, 0);
        LocalTime slotEnd = LocalTime.of(19, 0);

        while (slotStart.isBefore(slotEnd)) {
            LocalTime start = slotStart;
            LocalTime end = slotStart.plusHours(1);

            boolean isBooked = existingBookings.stream().anyMatch(b -> overlaps(b, start, end));

            slots.add(BookingDto.SlotAvailability.builder()
                    .startTime(start)
                    .endTime(end)
                    .available(!isBooked)
                    .build());

            slotStart = end;
        }
        return slots;
    }

    // ── Studio holiday / closure management ──

    public List<BookingDto.BlockedDateResponse> blockDates(BookingDto.BlockDatesRequest request) {
        List<BlockedDate> saved = request.getDates().stream()
                .filter(d -> !blockedDateRepository.existsByDate(d))
                .map(d -> blockedDateRepository.save(BlockedDate.builder().date(d).reason(request.getReason()).build()))
                .collect(Collectors.toList());
        return saved.stream().map(this::toBlockedDateResponse).collect(Collectors.toList());
    }

    @Transactional
    public void unblockDate(LocalDate date) {
        blockedDateRepository.deleteByDate(date);
    }

    @Transactional(readOnly = true)
    public List<BookingDto.BlockedDateResponse> listBlockedDates(LocalDate from, LocalDate to) {
        List<BlockedDate> dates = (from != null && to != null)
                ? blockedDateRepository.findByDateBetweenOrderByDateAsc(from, to)
                : blockedDateRepository.findByDateGreaterThanEqualOrderByDateAsc(LocalDate.now());
        return dates.stream().map(this::toBlockedDateResponse).collect(Collectors.toList());
    }

    private BookingDto.BlockedDateResponse toBlockedDateResponse(BlockedDate b) {
        return BookingDto.BlockedDateResponse.builder().id(b.getId()).date(b.getDate()).reason(b.getReason()).build();
    }

    private Booking findById(String id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
    }

    /** True if [start,end) overlaps the booking's [start,end) time range. */
    private boolean overlaps(Booking b, LocalTime start, LocalTime end) {
        LocalTime existingStart = b.getStartTime();
        LocalTime existingEnd = b.getEndTime() != null ? b.getEndTime() : existingStart.plusHours(1);
        return existingStart.isBefore(end) && start.isBefore(existingEnd);
    }

    private volatile String bookingCounterMonth = "";

    /**
     * Generates the next sequential booking number for the current month
     * (e.g. BK26070001). Uses the process-local {@code bookingCounter} for
     * atomic in-JVM increments — this used to silently call
     * {@code bookingRepository.count() + 1} instead, which is not safe under
     * concurrent requests (two simultaneous bookings could compute the same
     * count and collide on the DB's unique booking_number constraint). The
     * counter is (re)seeded from the DB whenever the month rolls over or on
     * first use, so numbers never collide with existing rows even across
     * app restarts; createBooking() also retries once on a genuine DB-level
     * collision as a last-resort safety net for multi-instance deployments.
     */
    private synchronized String generateBookingNumber() {
        String prefix = "BK" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        if (!prefix.equals(bookingCounterMonth)) {
            long existingForMonth = bookingRepository.count(); // seed conservatively on rollover
            bookingCounter.set(existingForMonth);
            bookingCounterMonth = prefix;
        }
        long next = bookingCounter.incrementAndGet();
        return prefix + String.format("%04d", next);
    }

    private BookingDto.BookingResponse toResponse(Booking b) {
        return BookingDto.BookingResponse.builder()
                .id(b.getId())
                .bookingNumber(b.getBookingNumber())
                .clientId(b.getClient() != null ? b.getClient().getId() : null)
                .clientName(b.getClientName())
                .clientMobile(b.getClientMobile())
                .clientEmail(b.getClientEmail())
                .bookingDate(b.getBookingDate())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .packageId(b.getPackageId())
                .packageName(b.getPackageName())
                .type(b.getType().name())
                .shootCategory(b.getShootCategory() != null ? b.getShootCategory().name() : null)
                .status(b.getStatus().name())
                .photographerId(b.getPhotographer() != null ? b.getPhotographer().getId() : null)
                .photographerName(b.getPhotographer() != null ? b.getPhotographer().getFullName() : null)
                .venue(b.getVenue())
                .locationType(b.getLocationType() != null ? b.getLocationType().name() : "INDOOR")
                .pickupStatus(b.getPickedUpAt() != null ? "PICKED_UP" : b.getReadyForPickupAt() != null ? "AWAITING_PICKUP" : "NOT_READY")
                .readyForPickupAt(b.getReadyForPickupAt())
                .pickedUpAt(b.getPickedUpAt())
                .pickupReminderCount(b.getPickupReminderCount())
                .lastPickupReminderAt(b.getLastPickupReminderAt())
                .daysAwaitingPickup(b.getReadyForPickupAt() != null && b.getPickedUpAt() == null
                        ? java.time.temporal.ChronoUnit.DAYS.between(b.getReadyForPickupAt(), java.time.LocalDateTime.now()) : null)
                .notes(b.getNotes())
                .advanceAmount(b.getAdvanceAmount())
                .totalAmount(b.getTotalAmount())
                .balanceAmount(b.getBalanceAmount())
                .cancelledReason(b.getCancelledReason())
                .rescheduledCount(b.getRescheduledCount())
                .orderStage(b.getOrderStage() != null ? b.getOrderStage().name() : null)
                .orderStageLabel(b.getOrderStage() != null ? b.getOrderStage().getLabel() : null)
                .expectedDeliveryDate(b.getExpectedDeliveryDate())
                .editorId(b.getEditor() != null ? b.getEditor().getId() : null)
                .editorName(b.getEditor() != null ? b.getEditor().getFullName() : null)
                .courierProvider(b.getCourierProvider())
                .courierTrackingNumber(b.getCourierTrackingNumber())
                .deliveredAt(b.getDeliveredAt())
                .stageHistory(b.getStageHistory() == null ? java.util.Collections.emptyList() :
                        b.getStageHistory().stream().map(h -> BookingDto.StageHistoryEntry.builder()
                                .stage(h.getStage().name())
                                .stageLabel(h.getStage().getLabel())
                                .note(h.getNote())
                                .changedByName(h.getChangedByName())
                                .createdAt(h.getCreatedAt())
                                .build()).collect(Collectors.toList()))
                .marketingOptIn(b.isMarketingOptIn())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private BookingDto.CalendarEvent toCalendarEvent(Booking b) {
        return BookingDto.CalendarEvent.builder()
                .id(b.getId())
                .bookingNumber(b.getBookingNumber())
                .title(b.getClientName() + " - " + (b.getPackageName() != null ? b.getPackageName() : b.getType().name()))
                .date(b.getBookingDate())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .status(b.getStatus().name())
                .type(b.getType().name())
                .shootCategory(b.getShootCategory() != null ? b.getShootCategory().name() : null)
                .clientName(b.getClientName())
                .photographerName(b.getPhotographer() != null ? b.getPhotographer().getFullName() : null)
                .build();
    }
}
