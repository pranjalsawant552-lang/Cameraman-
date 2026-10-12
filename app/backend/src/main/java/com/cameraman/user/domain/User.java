package com.cameraman.user.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import com.cameraman.role.domain.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_mobile", columnList = "mobile"),
    @Index(name = "idx_users_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseAuditEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 15)
    private String mobile;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(name = "email_verified")
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "mobile_verified")
    @Builder.Default
    private boolean mobileVerified = false;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

    @Column(name = "remember_me_token")
    private String rememberMeToken;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Client-level marketing/re-engagement consent — separate from a specific booking's
     *  own flag (BookingDto.marketingOptIn) so a returning client with several bookings
     *  can opt out account-wide in one place (see PUT /users/me/marketing-preference)
     *  rather than having to have opted out on every individual booking. */
    @Column(name = "marketing_opt_in", nullable = false)
    @Builder.Default
    private boolean marketingOptIn = true;

    /** "MODULE:ACTION" codes granted through the user's active CUSTOM roles. The frontend uses
     *  these to decide which menu items to show; the server enforces them via the
     *  {@code CUSTOM:MODULE:ACTION} authorities. */
    public List<String> getCustomPermissionCodes() {
        return roles.stream()
                .filter(r -> r.isActive() && !r.isBuiltIn())
                .flatMap(r -> r.getPermissions().stream())
                .filter(p -> p.isActive())
                .map(p -> p.getModule() + ":" + p.getAction())
                .distinct().sorted().collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                // A deactivated role grants nothing
                .filter(Role::isActive)
                .flatMap(role -> {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
                    boolean custom = !role.isBuiltIn();
                    role.getPermissions().stream().filter(p -> p.isActive()).forEach(p -> {
                        authorities.add(new SimpleGrantedAuthority(p.getModule() + ":" + p.getAction()));
                        // Custom roles reach endpoints through these (see the controllers'
                        // @PreAuthorize expressions); built-in roles keep their fixed role-name access.
                        if (custom) {
                            authorities.add(new SimpleGrantedAuthority("CUSTOM:" + p.getModule() + ":" + p.getAction()));
                        }
                    });
                    return authorities.stream();
                })
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        if (lockedUntil == null) return true;
        if (LocalDateTime.now().isAfter(lockedUntil)) {
            lockedUntil = null;
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        // PENDING_VERIFICATION accounts (self-registered clients who haven't verified their
        // email yet) must be able to sign in with the password they just chose; AuthService.login
        // promotes them to ACTIVE on first success. Only deactivated/suspended/deleted accounts
        // are blocked. Previously this was ACTIVE-only, so Spring's authenticate() rejected new
        // clients before that promotion could ever run.
        return status == UserStatus.ACTIVE || status == UserStatus.PENDING_VERIFICATION;
    }

    public String getFullName() {
        return firstName + (lastName != null ? " " + lastName : "");
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void lockAccount(int durationMinutes) {
        this.lockedUntil = LocalDateTime.now().plusMinutes(durationMinutes);
    }
}
