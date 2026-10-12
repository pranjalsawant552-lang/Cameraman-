package com.cameraman.booking.controller;

import com.cameraman.booking.dto.BookingDto;
import com.cameraman.booking.service.BookingService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Booking management")
public class BookingController {

    private final BookingService bookingService;
    private final com.cameraman.billing.service.BillingService billingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','CLIENT') or hasAuthority('CUSTOM:BOOKING:CREATE')")
    @Operation(summary = "Create booking (staff can book for any client; clients can only book for themselves)")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> create(
            @Valid @RequestBody BookingDto.CreateRequest request,
            @CurrentUser String userId,
            Authentication authentication) {
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("CUSTOM:BOOKING:VIEW"));
        if (!isStaff) {
            // A client can never book on someone else's behalf, no matter what
            // clientId/clientName was sent from the browser.
            request.setClientId(userId);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created", bookingService.createBooking(request, userId, !isStaff)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER') or hasAuthority('CUSTOM:BOOKING:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<BookingDto.BookingResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String locationType,
            @RequestParam(required = false) String pickup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "bookingDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getAllBookings(search, status, locationType, pickup, PageRequest.of(page, size, sort))));
    }

    @PostMapping("/{id}/ready-for-pickup")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Mark the finished photos/order as ready for the client to collect")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> markReadyForPickup(@PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Marked ready for pickup", bookingService.markReadyForPickup(id, userId)));
    }

    @PostMapping("/{id}/picked-up")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Record that the client collected the order")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> markPickedUp(@PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Marked as picked up", bookingService.markPickedUp(id, userId)));
    }

    @PostMapping("/{id}/pickup-reminder")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Send the client a reminder (WhatsApp/email) to collect their ready order")
    public ResponseEntity<ApiResponse<BookingDto.PickupReminderResult>> sendPickupReminder(@PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Reminder sent", bookingService.sendPickupReminder(id, userId)));
    }

    @PostMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Create (or fetch) the invoice for a booking")
    public ResponseEntity<ApiResponse<com.cameraman.billing.dto.BillingDto.InvoiceResponse>> generateInvoice(@PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Invoice ready", billingService.createInvoiceForBooking(id, userId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> getById(
            @PathVariable String id, @CurrentUser String userId, Authentication authentication) {
        BookingDto.BookingResponse booking = bookingService.getById(id);
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:BOOKING:VIEW"));
        if (!isStaff && !userId.equals(booking.getClientId())) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Booking", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success(booking));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.UpdateRequest request,
            @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Booking updated",
                bookingService.updateBooking(id, request, userId)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> confirm(
            @PathVariable String id, @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Booking confirmed", bookingService.confirmBooking(id, userId)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','CLIENT') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> reschedule(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.RescheduleRequest request,
            @CurrentUser String userId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Booking rescheduled",
                bookingService.reschedule(id, request, userId, isClientOnly(authentication))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','CLIENT') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> cancel(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.CancelRequest request,
            @CurrentUser String userId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
                bookingService.cancelBooking(id, request, userId, isClientOnly(authentication))));
    }

    /** True when the caller holds only the CLIENT role (no staff role) — used to apply
     *  self-service ownership + policy-window restrictions in the service layer. */
    private boolean isClientOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("ROLE_PHOTOGRAPHER")
                        || a.getAuthority().equals("ROLE_EDITOR")
                        || a.getAuthority().equals("CUSTOM:BOOKING:VIEW"));
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','PHOTOGRAPHER','EDITOR') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Advance/update the booking's order-pipeline stage (Booking Confirmed -> ... -> Delivered)")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> updateStage(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.UpdateStageRequest request,
            @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Order stage updated",
                bookingService.updateOrderStage(id, request, userId)));
    }

    @PatchMapping("/{id}/courier")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Set/update courier provider and tracking number for a dispatched order")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> updateCourier(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.UpdateCourierRequest request,
            @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Courier info updated",
                bookingService.updateCourierInfo(id, request, userId)));
    }

    @PatchMapping("/{id}/editor")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Assign the editor responsible for post-production on this booking")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> assignEditor(
            @PathVariable String id,
            @Valid @RequestBody BookingDto.AssignEditorRequest request,
            @CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Editor assigned",
                bookingService.assignEditor(id, request, userId)));
    }

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingDto.CalendarEvent>>> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getCalendarEvents(from, to)));
    }

    @PostMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingDto.SlotAvailability>>> checkAvailability(
            @Valid @RequestBody BookingDto.AvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.checkAvailability(request)));
    }

    @GetMapping("/blocked-dates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List studio-closed dates (holidays/maintenance) — used by calendars/date pickers to disable those days")
    public ResponseEntity<ApiResponse<List<BookingDto.BlockedDateResponse>>> listBlockedDates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.listBlockedDates(from, to)));
    }

    @PostMapping("/blocked-dates")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BOOKING:UPDATE')")
    @Operation(summary = "Block one or more dates (bulk) so no new bookings can be made on them")
    public ResponseEntity<ApiResponse<List<BookingDto.BlockedDateResponse>>> blockDates(
            @Valid @RequestBody BookingDto.BlockDatesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Dates blocked", bookingService.blockDates(request)));
    }

    @DeleteMapping("/blocked-dates/{date}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:BOOKING:DELETE')")
    @Operation(summary = "Unblock a previously-blocked date")
    public ResponseEntity<ApiResponse<Void>> unblockDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        bookingService.unblockDate(date);
        return ResponseEntity.ok(ApiResponse.success("Date unblocked"));
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:BOOKING:VIEW') or #clientId == authentication.principal.id")
    public ResponseEntity<ApiResponse<PagedResponse<BookingDto.BookingResponse>>> getClientBookings(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getClientBookings(clientId, PageRequest.of(page, size, Sort.by("bookingDate").descending()))));
    }
}
