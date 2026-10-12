package com.cameraman.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class UserDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateUserRequest {
        @NotBlank @Email private String email;
        private String password;
        @NotBlank @Size(min = 2, max = 60) private String firstName;
        @Size(max = 60) private String lastName;
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number") private String mobile;
        private String address;
        private String notes;
        private List<String> roleNames;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String mobile;
        private String address;
        private String notes;
        private String status;
        private List<String> roleNames;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateProfileRequest {
        private String firstName;
        private String lastName;
        private String mobile;
        private String address;
        private String profilePhotoUrl;
    }

    @Getter @Setter @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserResponse {
        private String id;
        private String email;
        private String mobile;
        private String firstName;
        private String lastName;
        private String profilePhotoUrl;
        private String status;
        private boolean emailVerified;
        private boolean mobileVerified;
        private List<String> roles;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /** Only populated by GET /users/clients/search (see
         *  UserService#searchClients) — null everywhere else, since
         *  computing it isn't free and most callers of UserResponse don't
         *  need it. */
        private Long totalBookings;
        private java.math.BigDecimal totalSpend;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserProfile {
        private String id;
        private String email;
        private String mobile;
        private String firstName;
        private String lastName;
        private String profilePhotoUrl;
        private String status;
        private boolean emailVerified;
        private boolean mobileVerified;
        private List<String> roles;
        private List<String> permissions;
        private LocalDateTime createdAt;
        private boolean marketingOptIn;
    }
}
