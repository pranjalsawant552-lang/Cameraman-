package com.cameraman.auth.domain;

import com.cameraman.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_history", indexes = {
    @Index(name = "idx_login_history_user", columnList = "user_id"),
    @Index(name = "idx_login_history_at", columnList = "logged_in_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "logged_in_at", nullable = false)
    @Builder.Default
    private LocalDateTime loggedInAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_method")
    private LoginMethod loginMethod;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = true;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "location", length = 200)
    private String location;
}
