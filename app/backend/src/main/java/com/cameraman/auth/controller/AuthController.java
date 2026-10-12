package com.cameraman.auth.controller;

import com.cameraman.auth.dto.AuthDto;
import com.cameraman.auth.service.AuthService;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        AuthDto.AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please verify your email.", response));
    }

    @PostMapping("/create-client")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT')")
    @Operation(summary = "Staff creates a client account on their behalf (e.g. from the billing or gallery form) — no login tokens returned, client verifies by email and logs in via OTP")
    public ResponseEntity<ApiResponse<AuthDto.CreateClientResponse>> createClient(
            @Valid @RequestBody AuthDto.CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Client account created, verification email sent", authService.createClientByStaff(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email/mobile and password")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthDto.AuthResponse response = authService.login(request,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/login/otp")
    @Operation(summary = "Login with OTP")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> loginWithOtp(
            @Valid @RequestBody AuthDto.OtpLoginRequest request,
            HttpServletRequest httpRequest) {
        AuthDto.AuthResponse response = authService.loginWithOtp(request,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/login/firebase")
    @Operation(summary = "Passwordless login for a mobile number verified via Firebase Phone Auth")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> loginWithFirebase(
            @Valid @RequestBody AuthDto.FirebaseLoginRequest request,
            HttpServletRequest httpRequest) {
        AuthDto.AuthResponse response = authService.loginWithFirebase(request,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/mobile/verify-firebase")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Attach/confirm a Firebase-verified mobile number to the logged-in account")
    public ResponseEntity<ApiResponse<Void>> verifyMobileFirebase(
            @Valid @RequestBody AuthDto.VerifyMobileFirebaseRequest request,
            @CurrentUser String userId) {
        authService.verifyMobileFirebase(userId, request.getIdToken());
        return ResponseEntity.ok(ApiResponse.success("Mobile number verified"));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody AuthDto.SendOtpRequest request,
            HttpServletRequest httpRequest) {
        authService.sendOtpRequest(request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully"));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @Valid @RequestBody AuthDto.VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> refresh(
            @Valid @RequestBody AuthDto.RefreshTokenRequest request) {
        AuthDto.AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            @CurrentUser String userId) {
        String token = extractToken(request);
        authService.logout(token, userId);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Log out of all devices (revokes every refresh token)")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest request, @CurrentUser String userId) {
        authService.logoutAll(extractToken(request), userId);
        return ResponseEntity.ok(ApiResponse.success("Logged out of all devices"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If this email is registered, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody AuthDto.ChangePasswordRequest request,
            @CurrentUser String userId) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
