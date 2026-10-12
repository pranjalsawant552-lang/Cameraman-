package com.cameraman.auth.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_records", indexes = {
    @Index(name = "idx_otp_identifier", columnList = "identifier"),
    @Index(name = "idx_otp_expires", columnList = "expires_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpRecord extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 150)
    private String identifier; // email or mobile

    @Column(nullable = false, length = 10)
    private String otp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "attempt_count")
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid(String inputOtp) {
        return !used && !isExpired() && otp.equals(inputOtp);
    }
}
