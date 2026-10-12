package com.cameraman.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;

public class AuthDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateClientRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 2, max = 60)
        private String firstName;

        @Size(max = 60)
        private String lastName;

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        private String mobile;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateClientResponse {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String mobile;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RegisterRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 100)
        private String password;

        @NotBlank @Size(min = 2, max = 60)
        private String firstName;

        @Size(max = 60)
        private String lastName;

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        private String mobile;

        // Optional: Firebase Phone Auth ID token proving the mobile number above
        // was already verified client-side (OTP sent & confirmed via Firebase).
        // If provided and it matches `mobile`, the account is created with
        // mobileVerified=true immediately - no separate verification step needed.
        private String mobileIdToken;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FirebaseLoginRequest {
        @NotBlank
        private String idToken;

        private String deviceId;
        private String deviceInfo;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerifyMobileFirebaseRequest {
        @NotBlank
        private String idToken;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LoginRequest {
        @NotBlank
        private String identifier; // email or mobile

        @NotBlank
        private String password;

        private boolean rememberMe;
        private String deviceId;
        private String deviceInfo;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OtpLoginRequest {
        @NotBlank
        private String identifier; // email or mobile

        @NotBlank @Size(min = 6, max = 6)
        private String otp;

        private String deviceId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendOtpRequest {
        @NotBlank
        private String identifier;

        @NotBlank
        private String purpose; // LOGIN, EMAIL_VERIFICATION, MOBILE_VERIFICATION, PASSWORD_RESET
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerifyOtpRequest {
        @NotBlank
        private String identifier;

        @NotBlank @Size(min = 6, max = 6)
        private String otp;

        @NotBlank
        private String purpose;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ForgotPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;

        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;

        @NotBlank
        private String confirmPassword;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;

        @NotBlank
        private String confirmPassword;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private long expiresIn;
        private UserInfo user;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserInfo {
        private String id;
        private String email;
        private String mobile;
        private String firstName;
        private String lastName;
        private String profilePhotoUrl;
        private String status;
        private java.util.List<String> roles;
        /** "MODULE:ACTION" codes from custom roles (empty for built-in roles). */
        private java.util.List<String> permissions;
        private boolean emailVerified;
        private boolean mobileVerified;
    }
}
