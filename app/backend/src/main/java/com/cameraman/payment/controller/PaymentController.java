package com.cameraman.payment.controller;

import com.cameraman.common.response.ApiResponse;
import com.cameraman.payment.dto.PaymentDto;
import com.cameraman.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing and management")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:PAYMENT:VIEW')")
    public ResponseEntity<ApiResponse<com.cameraman.common.response.PagedResponse<PaymentDto.PaymentResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(paymentService.list(search, status, pageable)));
    }

    @PostMapping("/razorpay/create-order")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentDto.RazorpayOrderResponse>> createOrder(
            @Valid @RequestBody PaymentDto.CreateOrderRequest request,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created",
                        paymentService.createRazorpayOrder(request, userId, isStaff(authentication))));
    }

    private boolean isStaff(org.springframework.security.core.Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ACCOUNTANT")
                        || a.getAuthority().equals("ROLE_RECEPTIONIST")
                        || a.getAuthority().equals("CUSTOM:PAYMENT:VIEW"));
    }

    @PostMapping("/razorpay/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> verifyPayment(
            @Valid @RequestBody PaymentDto.VerifyPaymentRequest request,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Payment verified",
                paymentService.verifyAndCapturePayment(request, userId, isStaff(authentication))));
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','ACCOUNTANT','RECEPTIONIST') or hasAuthority('CUSTOM:PAYMENT:CREATE')")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> recordManual(
            @Valid @RequestBody PaymentDto.ManualPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded", paymentService.recordManualPayment(request)));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER') or hasAuthority('CUSTOM:PAYMENT:REFUND')")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> refund(
            @PathVariable String id,
            @Valid @RequestBody PaymentDto.RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Refund processed", paymentService.processRefund(id, request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> getById(@PathVariable String id,
            @com.cameraman.common.security.CurrentUser String userId,
            org.springframework.security.core.Authentication authentication) {
        PaymentDto.PaymentResponse payment = paymentService.getById(id);
        // A client may only read their own payments; other ids look like "not found".
        if (!isStaff(authentication) && !userId.equals(payment.getClientId())) {
            throw new com.cameraman.common.exception.ResourceNotFoundException("Payment", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {
        paymentService.handleWebhook(signature, payload);
        return ResponseEntity.ok().build();
    }
}
