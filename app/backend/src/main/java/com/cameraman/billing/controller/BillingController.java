package com.cameraman.billing.controller;

import com.cameraman.billing.dto.BillingDto;
import com.cameraman.billing.service.BillingService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/billing/invoices")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Invoice and billing management")
public class BillingController {

    private final BillingService billingService;
    private final com.cameraman.billing.service.AutoInvoiceService autoInvoiceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','ACCOUNTANT') or hasAuthority('CUSTOM:INVOICE:CREATE')")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> create(
            @Valid @RequestBody BillingDto.CreateInvoiceRequest request,
            @com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice created", billingService.createInvoice(request, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','ACCOUNTANT') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody BillingDto.CreateInvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice updated", billingService.updateInvoice(id, request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<BillingDto.InvoiceResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getAllInvoices(search, status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> getById(
            @PathVariable String id,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        BillingDto.InvoiceResponse invoice = billingService.getById(id);
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ACCOUNTANT")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("CUSTOM:INVOICE:VIEW"));
        if (!isStaff && (!userId.equals(invoice.getClientId()) || "DRAFT".equals(invoice.getStatus()))) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Invoice", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the logged-in client's own invoices/bills")
    public ResponseEntity<ApiResponse<PagedResponse<BillingDto.InvoiceResponse>>> getMyInvoices(
            @com.cameraman.common.security.CurrentUser String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getMyInvoices(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> finalize(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Invoice finalized", billingService.finalizeInvoice(id)));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> recordPayment(
            @PathVariable String id, @Valid @RequestBody BillingDto.PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", billingService.recordPayment(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> cancel(
            @PathVariable String id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled", billingService.cancelInvoice(id, reason)));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String id,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        BillingDto.InvoiceResponse invoice = billingService.getById(id);
        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ACCOUNTANT")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("CUSTOM:INVOICE:VIEW"));
        if (!isStaff && (!userId.equals(invoice.getClientId()) || "DRAFT".equals(invoice.getStatus()))) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Invoice", "id", id);
        }
        byte[] pdf = billingService.downloadInvoicePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("invoice-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping("/{id}/send-whatsapp")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> sendWhatsApp(@PathVariable String id) {
        billingService.sendWhatsAppInvoice(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice sent via WhatsApp"));
    }

    @PostMapping("/{id}/send-email")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:UPDATE')")
    public ResponseEntity<ApiResponse<Void>> sendEmail(@PathVariable String id) {
        billingService.sendEmailInvoice(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice emailed"));
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Create invoices for every booking, studio session and outdoor shoot that has none yet")
    public ResponseEntity<ApiResponse<com.cameraman.billing.service.AutoInvoiceService.BackfillResult>> backfill(
            @com.cameraman.common.security.CurrentUser String userId) {
        return ResponseEntity.ok(ApiResponse.success("Invoices generated", autoInvoiceService.backfillMissing(userId)));
    }

    @PostMapping("/for-source/{sourceType}/{sourceId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:CREATE')")
    @Operation(summary = "Create (or fetch) the invoice for a booking, studio session or outdoor shoot")
    public ResponseEntity<ApiResponse<BillingDto.InvoiceResponse>> forSource(
            @PathVariable String sourceType, @PathVariable String sourceId,
            @com.cameraman.common.security.CurrentUser String userId) {
        BillingDto.InvoiceResponse inv = switch (sourceType.toUpperCase()) {
            case "BOOKING" -> billingService.createInvoiceForBooking(sourceId, userId);
            case "STUDIO_SESSION" -> billingService.createInvoiceForStudioSession(sourceId, userId);
            case "OUTDOOR_SHOOT" -> billingService.createInvoiceForOutdoorShoot(sourceId, userId);
            default -> throw new com.cameraman.common.exception.BusinessException("Unknown source type: " + sourceType, "INVALID_SOURCE");
        };
        return ResponseEntity.ok(ApiResponse.success("Invoice ready", inv));
    }

    @GetMapping("/by-client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:INVOICE:VIEW')")
    @Operation(summary = "All invoices for one client (staff view)")
    public ResponseEntity<ApiResponse<java.util.List<BillingDto.InvoiceResponse>>> byClient(@PathVariable String clientId) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoicesForClient(clientId)));
    }

    @GetMapping("/revenue-summary")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT') or hasAuthority('CUSTOM:INVOICE:VIEW')")
    public ResponseEntity<ApiResponse<BillingDto.RevenueSummary>> getRevenueSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getRevenueSummary(from, to)));
    }
}
