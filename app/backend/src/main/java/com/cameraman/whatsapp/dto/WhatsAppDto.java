package com.cameraman.whatsapp.dto;

import lombok.*;

public class WhatsAppDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendMessageRequest {
        private String mobile;
        private String message;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageStatus {
        private String messageId;
        private String status; // sent, delivered, read, failed
        private String mobile;
        private String timestamp;
    }
}
