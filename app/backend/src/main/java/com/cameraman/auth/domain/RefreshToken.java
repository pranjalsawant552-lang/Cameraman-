package com.cameraman.auth.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token", columnList = "token"),
    @Index(name = "idx_refresh_user", columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }
    public boolean isValid() { return !revoked && !isExpired(); }
}
