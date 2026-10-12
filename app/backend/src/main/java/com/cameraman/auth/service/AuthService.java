package com.cameraman.auth.service;

import com.cameraman.auth.domain.*;
import com.cameraman.auth.dto.AuthDto;
import com.cameraman.auth.repository.*;
import com.cameraman.common.exception.*;
import com.cameraman.common.security.JwtService;
import com.cameraman.common.util.OtpUtil;
import com.cameraman.notification.service.EmailService;
import com.cameraman.whatsapp.service.WhatsAppService;
import com.cameraman.role.domain.Role;
import com.cameraman.role.repository.RoleRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final FirebaseAuthService firebaseAuthService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${otp.max-attempts:3}")
    private int maxOtpAttempts;

    @Value("${security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${security.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${app.otp.log-to-console:false}")
    private boolean otpLogToConsole;

    /**
     * Staff creates a client account on the client's behalf — used from the billing
     * form when the person being billed doesn't have an account yet ("new client"
     * toggle). Unlike self-registration, this never returns login tokens (it's not
     * the client logging themselves in) and doesn't take a password: the account
     * gets a random, never-disclosed password, and the client logs in later via
     * OTP (already the primary login path for clients) once they verify their
     * email. Reuses the exact same verification-email mechanism as self-registration.
     */
    public AuthDto.CreateClientResponse createClientByStaff(AuthDto.CreateClientRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (request.getMobile() != null && !request.getMobile().isBlank() && userRepository.existsByMobile(request.getMobile())) {
            throw new DuplicateResourceException("User", "mobile", request.getMobile());
        }

        Role clientRole = roleRepository.findByName("CLIENT")
                .orElseThrow(() -> new ResourceNotFoundException("Default role CLIENT not found"));

        String randomPassword = UUID.randomUUID().toString(); // never shown to anyone; client logs in via OTP instead

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(randomPassword))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .status(UserStatus.PENDING_VERIFICATION)
                .roles(new HashSet<>(Set.of(clientRole)))
                .emailVerified(false)
                .mobileVerified(false)
                .build();
        user = userRepository.save(user);

        try {
            sendOtp(request.getEmail(), OtpPurpose.EMAIL_VERIFICATION, null);
        } catch (Exception e) {
            log.error("Failed to send verification email to new client {}: {}", request.getEmail(), e.getMessage());
        }

        return AuthDto.CreateClientResponse.builder()
                .id(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName())
                .email(user.getEmail()).mobile(user.getMobile())
                .build();
    }

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (request.getMobile() != null && userRepository.existsByMobile(request.getMobile())) {
            throw new DuplicateResourceException("User", "mobile", request.getMobile());
        }

        Role clientRole = roleRepository.findByName("CLIENT")
                .orElseThrow(() -> new ResourceNotFoundException("Default role CLIENT not found"));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .status(UserStatus.PENDING_VERIFICATION)
                .roles(new HashSet<>(Set.of(clientRole)))
                .emailVerified(false)
                .mobileVerified(false)
                .build();

        // Mobile OTP is now handled entirely by Firebase Phone Auth: the
        // frontend sends & confirms the OTP itself via Google's infrastructure
        // (invisible reCAPTCHA + Firebase JS SDK) *before* calling /register,
        // then passes us the resulting ID token as proof. We just verify it
        // server-side and trust the phone number it contains.
        if (request.getMobile() != null && !request.getMobile().isBlank()
                && request.getMobileIdToken() != null && !request.getMobileIdToken().isBlank()) {
            String verifiedPhone = firebaseAuthService.verifyPhoneToken(request.getMobileIdToken());
            if (verifiedPhone != null &&
                    firebaseAuthService.lastTenDigits(verifiedPhone).equals(request.getMobile())) {
                user.setMobileVerified(true);
            } else {
                log.warn("Firebase-verified phone {} did not match submitted mobile {}; leaving unverified",
                        verifiedPhone, request.getMobile());
            }
        }

        user = userRepository.save(user);

        // Send verification email
        try {
            sendOtp(request.getEmail(), OtpPurpose.EMAIL_VERIFICATION, null);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", request.getEmail(), e.getMessage());
        }

        return buildAuthResponse(user, null, null);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailOrMobile(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Check account lock
        if (!user.isAccountNonLocked()) {
            recordLoginHistory(user, ipAddress, userAgent, LoginMethod.EMAIL_PASSWORD, false, "Account locked");
            throw new BusinessException("Account is locked until " + user.getLockedUntil(), "ACCOUNT_LOCKED");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            handleFailedLogin(user, ipAddress, userAgent);
            throw new BadCredentialsException("Invalid credentials");
        } catch (org.springframework.security.authentication.DisabledException e) {
            recordLoginHistory(user, ipAddress, userAgent, LoginMethod.EMAIL_PASSWORD, false, "Account disabled");
            throw new BusinessException("This account has been deactivated. Please contact the studio.", "ACCOUNT_DISABLED");
        }

        user.resetFailedAttempts();
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
        recordLoginHistory(user, ipAddress, userAgent, LoginMethod.EMAIL_PASSWORD, true, null);

        String accessToken = generateAccessToken(user);
        // Always issue a refresh token. It used to be issued only for rememberMe/deviceId,
        // which the web app never sent — so the 15-minute access token could not be renewed
        // and users were thrown out mid-work. Idle sign-out is handled separately by the
        // configurable inactivity timeout in Settings.
        String refreshToken = createRefreshToken(user, request.getDeviceId(), ipAddress, userAgent);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthDto.AuthResponse loginWithOtp(AuthDto.OtpLoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailOrMobile(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isAccountNonLocked()) {
            throw new BusinessException("Account is locked", "ACCOUNT_LOCKED");
        }

        OtpRecord otpRecord = otpRepository
                .findTopByIdentifierAndPurposeAndUsedFalseOrderByCreatedAtDesc(request.getIdentifier(), OtpPurpose.LOGIN)
                .orElseThrow(() -> new OtpException("No valid OTP found. Please request a new one."));

        if (otpRecord.getAttemptCount() >= maxOtpAttempts) {
            throw new OtpException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        if (!otpRecord.isValid(request.getOtp())) {
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
            otpRepository.save(otpRecord);
            throw new OtpException("Invalid or expired OTP");
        }

        otpRecord.setUsed(true);
        otpRepository.save(otpRecord);

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        recordLoginHistory(user, ipAddress, userAgent, LoginMethod.EMAIL_OTP, true, null);

        String accessToken = generateAccessToken(user);
        String refreshToken = createRefreshToken(user, request.getDeviceId(), ipAddress, userAgent);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Passwordless login for a mobile number verified via Firebase Phone Auth.
     * The frontend runs the Firebase phone sign-in flow (OTP sent & confirmed
     * by Google), then hands us the resulting ID token here.
     */
    public AuthDto.AuthResponse loginWithFirebase(AuthDto.FirebaseLoginRequest request,
                                                   String ipAddress, String userAgent) {
        String verifiedPhone = firebaseAuthService.verifyPhoneToken(request.getIdToken());
        if (verifiedPhone == null) {
            throw new BusinessException("This Firebase token has no verified phone number", "NO_PHONE_CLAIM");
        }
        String mobile = firebaseAuthService.lastTenDigits(verifiedPhone);

        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this mobile number"));

        if (!user.isAccountNonLocked()) {
            throw new BusinessException("Account is locked", "ACCOUNT_LOCKED");
        }

        // A successful Firebase phone sign-in is itself proof of ownership -
        // mark the number verified if it wasn't already.
        if (!user.isMobileVerified()) {
            user.setMobileVerified(true);
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        recordLoginHistory(user, ipAddress, userAgent, LoginMethod.MOBILE_OTP, true, null);

        String accessToken = generateAccessToken(user);
        String refreshToken = createRefreshToken(user, request.getDeviceId(), ipAddress, userAgent);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Attaches/confirms a Firebase-verified mobile number to the currently
     * logged-in user's account (e.g. from Profile Settings if they skipped
     * mobile verification at signup, or need to change their number).
     */
    public void verifyMobileFirebase(String userId, String idToken) {
        String verifiedPhone = firebaseAuthService.verifyPhoneToken(idToken);
        if (verifiedPhone == null) {
            throw new BusinessException("This Firebase token has no verified phone number", "NO_PHONE_CLAIM");
        }
        String mobile = firebaseAuthService.lastTenDigits(verifiedPhone);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Optional<User> existingOwner = userRepository.findByMobile(mobile);
        if (existingOwner.isPresent() && !existingOwner.get().getId().equals(userId)) {
            throw new DuplicateResourceException("User", "mobile", mobile);
        }

        user.setMobile(mobile);
        user.setMobileVerified(true);
        userRepository.save(user);
    }

    public AuthDto.AuthResponse refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new TokenException("Invalid or expired refresh token"));

        if (!refreshToken.isValid()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new TokenException("Refresh token has expired. Please log in again.");
        }

        User user = refreshToken.getUser();

        // Rotate refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newAccessToken = generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user,
                refreshToken.getDeviceId(), refreshToken.getIpAddress(), refreshToken.getDeviceInfo());

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    public void logout(String token, String userId) {
        // Blacklist access token
        try {
            Date expiration = jwtService.extractExpiration(token);
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set("blacklist:token:" + token, "logout", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Could not blacklist token: {}", e.getMessage());
        }

        // Revoke all refresh tokens for user
        refreshTokenRepository.revokeAllUserTokens(userId);
    }

    /** Signs the user out everywhere: blacklists the presented access token and revokes every refresh token. */
    public void logoutAll(String token, String userId) {
        logout(token, userId);
    }

    public void sendOtpRequest(AuthDto.SendOtpRequest request, String ipAddress) {
        OtpPurpose purpose = OtpPurpose.valueOf(request.getPurpose());
        sendOtp(request.getIdentifier(), purpose, ipAddress);
    }

    public void verifyOtp(AuthDto.VerifyOtpRequest request) {
        OtpPurpose purpose = OtpPurpose.valueOf(request.getPurpose());

        OtpRecord otpRecord = otpRepository
                .findTopByIdentifierAndPurposeAndUsedFalseOrderByCreatedAtDesc(request.getIdentifier(), purpose)
                .orElseThrow(() -> new OtpException("No valid OTP found"));

        if (!otpRecord.isValid(request.getOtp())) {
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
            otpRepository.save(otpRecord);
            throw new OtpException("Invalid or expired OTP");
        }

        otpRecord.setUsed(true);
        otpRepository.save(otpRecord);

        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            userRepository.findByEmail(request.getIdentifier()).ifPresent(user -> {
                user.setEmailVerified(true);
                if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                    user.setStatus(UserStatus.ACTIVE);
                }
                userRepository.save(user);
            });
        } else if (purpose == OtpPurpose.MOBILE_VERIFICATION) {
            userRepository.findByMobile(request.getIdentifier()).ifPresent(user -> {
                user.setMobileVerified(true);
                userRepository.save(user);
            });
        }
    }

    /** SHA-256 hex of a reset token. Only the hash is stored, so a database leak can't be replayed. */
    static String hashToken(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Always completes silently, whether or not the email belongs to an account, so the endpoint
     * can't be used to discover registered addresses. The raw token goes only in the email.
     */
    public void forgotPassword(AuthDto.ForgotPasswordRequest request) {
        Optional<User> found = userRepository.findByEmail(request.getEmail());
        if (found.isEmpty()) {
            log.debug("Password reset requested for unknown email");
            return;
        }
        User user = found.get();

        String resetToken = UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(hashToken(resetToken));
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), resetToken);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }

    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }

        User user = userRepository.findByPasswordResetToken(hashToken(request.getToken()))
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token", "INVALID_RESET_TOKEN"));

        if (user.getPasswordResetExpires() == null ||
            LocalDateTime.now().isAfter(user.getPasswordResetExpires())) {
            throw new BusinessException("Password reset token has expired", "RESET_TOKEN_EXPIRED");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepository.save(user);

        // Revoke all sessions
        refreshTokenRepository.revokeAllUserTokens(user.getId());
    }

    public void changePassword(String userId, AuthDto.ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect", "WRONG_PASSWORD");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(userId);
    }

    private void sendOtp(String identifier, OtpPurpose purpose, String ipAddress) {
        // Rate limit OTP requests
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentCount = otpRepository.countByIdentifierAndPurposeAndCreatedAtAfter(identifier, purpose, oneHourAgo);
        if (recentCount >= 5) {
            throw new OtpException("Too many OTP requests. Please wait before requesting again.");
        }

        otpRepository.invalidateAllForIdentifier(identifier, purpose);

        String otp = OtpUtil.generateOtp(6);
        OtpRecord record = OtpRecord.builder()
                .identifier(identifier)
                .otp(otp)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .ipAddress(ipAddress)
                .build();
        otpRepository.save(record);

        if (otpLogToConsole) {
            log.warn("\n==================== DEV OTP ====================\n" +
                            " identifier : {}\n purpose    : {}\n OTP        : {}\n expires in : {} min\n" +
                            "==================================================",
                    identifier, purpose, otp, otpExpiryMinutes);
        }

        if (identifier.contains("@")) {
            emailService.sendOtpEmail(identifier, otp, purpose.name());
        } else {
            // Mobile OTP no longer goes through a paid SMS gateway (e.g. Fast2SMS).
            // Registration and login now use Firebase Phone Auth instead - see
            // register()/loginWithFirebase()/verifyMobileFirebase(), where the
            // OTP is sent & confirmed entirely client-side by the Firebase JS
            // SDK and this backend only verifies the resulting ID token.
            // WhatsApp remains available as a fallback channel if configured,
            // for any caller still using the generic /otp/send endpoint.
            if (whatsAppService.isConfigured()) {
                whatsAppService.sendOtp(identifier, otp, otpExpiryMinutes);
            } else {
                throw new BusinessException(
                        "Mobile OTP via SMS is no longer supported. Please use Firebase phone " +
                                "verification instead (see /auth/mobile/verify-firebase or /auth/login/firebase).",
                        "USE_FIREBASE_PHONE_AUTH");
            }
        }
    }

    private String createRefreshToken(User user, String deviceId, String ipAddress, String deviceInfo) {
        String token = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
                .deviceId(deviceId)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()));
        claims.put("userId", user.getId());
        return jwtService.generateAccessToken(user.getEmail(), claims);
    }

    private void handleFailedLogin(User user, String ipAddress, String userAgent) {
        user.incrementFailedAttempts();
        if (user.getFailedLoginAttempts() >= maxLoginAttempts) {
            user.lockAccount(lockoutDurationMinutes);
            log.warn("Account locked for user {} after {} failed attempts", user.getEmail(), maxLoginAttempts);
        }
        userRepository.save(user);
        recordLoginHistory(user, ipAddress, userAgent, LoginMethod.EMAIL_PASSWORD, false, "Invalid password");
    }

    private void recordLoginHistory(User user, String ipAddress, String userAgent,
                                    LoginMethod method, boolean success, String failureReason) {
        LoginHistory history = LoginHistory.builder()
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginMethod(method)
                .success(success)
                .failureReason(failureReason)
                .build();
        loginHistoryRepository.save(history);
    }

    private AuthDto.AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        AuthDto.UserInfo userInfo = AuthDto.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .status(user.getStatus().name())
                .roles(roles)
                .permissions(user.getCustomPermissionCodes())
                .emailVerified(user.isEmailVerified())
                .mobileVerified(user.isMobileVerified())
                .build();

        return AuthDto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .user(userInfo)
                .build();
    }
}
