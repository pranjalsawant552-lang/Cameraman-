package com.cameraman.contact.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

public class ContactDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InquiryRequest {
        @NotBlank @Size(min = 2, max = 150)
        private String name;

        @NotBlank @Email @Size(max = 150)
        private String email;

        @Pattern(regexp = "^[6-9]\\d{9}$|^$", message = "Invalid Indian mobile number")
        private String mobile;

        @NotBlank @Size(min = 5, max = 2000)
        private String message;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InquiryResponse {
        private String id;
        private String name;
        private String email;
        private String mobile;
        private String message;
        private String status;
        private LocalDateTime createdAt;
        private int replyCount;
        private LocalDateTime lastRepliedAt;
        private String lastRepliedBy;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ReplyRequest {
        /** EMAIL, PHONE, WHATSAPP or OTHER. */
        @NotBlank @Pattern(regexp = "EMAIL|PHONE|WHATSAPP|OTHER", message = "Channel must be EMAIL, PHONE, WHATSAPP or OTHER")
        private String channel;

        /** What was said (or the email text). */
        @NotBlank @Size(min = 2, max = 4000)
        private String message;

        /** EMAIL channel only: really send the message to the visitor's address from here. */
        private boolean sendEmail;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReplyResponse {
        private String id;
        private String channel;
        private String message;
        private boolean emailed;
        private String repliedByName;
        private LocalDateTime createdAt;
    }
}
