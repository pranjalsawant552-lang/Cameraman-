package com.cameraman.common.exception;

import com.cameraman.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "DUPLICATE_RESOURCE"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ApiResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.validationError("Validation failed", errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        List<ApiResponse.FieldError> errors = ex.getFieldErrors()
                .stream()
                .map(fe -> ApiResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.validationError("Validation failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials", "INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ApiResponse.error("Account is locked. Please try again later.", "ACCOUNT_LOCKED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", "ACCESS_DENIED"));
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenException(TokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), "TOKEN_INVALID"));
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpException(OtpException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "OTP_ERROR"));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageException(StorageException ex) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage(), "STORAGE_ERROR"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds maximum allowed limit", "FILE_TOO_LARGE"));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentException(PaymentException ex) {
        log.error("Payment error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "PAYMENT_ERROR"));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage(), "RATE_LIMIT_EXCEEDED"));
    }

    /** A malformed request body — most often a value that doesn't fit the field's declared
     *  type (a string where a number was expected, an unparseable date, invalid JSON
     *  syntax). Before this handler, requests like this fell through to the generic
     *  Exception handler below and returned "An unexpected error occurred. Please try
     *  again." with a 500 status — which is both wrong (it's the caller's malformed
     *  request, a 400, not a server-side failure) and unhelpful (gives no hint what was
     *  actually wrong with the request, only visible in the server log's stack trace,
     *  which the person filing the bug report can't see). This is exactly the kind of
     *  failure that was almost certainly behind the still-unresolved Outdoor Shoots
     *  "New Shoot" report — every avenue in the create() logic itself was checked and
     *  ruled out, which points at the request payload itself, not the business logic. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("One of the values in this request wasn't in the expected format — " +
                        "double-check numbers, dates, and dropdown selections and try again.", "MALFORMED_REQUEST"));
    }

    /** A URL/query-parameter that doesn't match its expected type — e.g. a non-numeric
     *  "page" or "size" param, or a malformed date in a query string. Same reasoning as
     *  the handler above: was previously an opaque 500, now a clear 400. */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("The value provided for '" + ex.getName() + "' isn't valid.", "INVALID_PARAMETER"));
    }

    /** Thrown by Java's Enum.valueOf(...) — every controller in this app that converts a
     *  request field into an enum (category, status, direction, and similar) does this
     *  manually with no validation guard, so any value that doesn't exactly match a known
     *  enum constant throws this, unhandled, straight into the generic 500 below. Frontend
     *  dropdowns normally send well-formed values, but this closes the gap for anything
     *  else that reaches these endpoints — a stale cached frontend build, a direct API
     *  call, a future dropdown option added on one side and not the other. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("This request contains an invalid value: " + ex.getMessage(), "INVALID_VALUE"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again.", "INTERNAL_ERROR"));
    }
}
