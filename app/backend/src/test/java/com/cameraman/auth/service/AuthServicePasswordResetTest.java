package com.cameraman.auth.service;

import com.cameraman.auth.dto.AuthDto;
import com.cameraman.auth.repository.RefreshTokenRepository;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.notification.service.EmailService;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServicePasswordResetTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @InjectMocks private AuthService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("u1");
        user.setEmail("asha@example.com");
        user.setFirstName("Asha");
    }

    private AuthDto.ForgotPasswordRequest forgot(String email) {
        AuthDto.ForgotPasswordRequest r = new AuthDto.ForgotPasswordRequest();
        r.setEmail(email);
        return r;
    }

    private AuthDto.ResetPasswordRequest reset(String token, String pw, String confirm) {
        AuthDto.ResetPasswordRequest r = new AuthDto.ResetPasswordRequest();
        r.setToken(token);
        r.setNewPassword(pw);
        r.setConfirmPassword(confirm);
        return r;
    }

    @Test
    void forgotPassword_unknownEmail_returnsSilently_andSendsNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        service.forgotPassword(forgot("nobody@example.com")); // must not throw
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPassword_knownEmail_storesOnlyTheHash_andEmailsTheRawToken() {
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword(forgot("asha@example.com"));

        ArgumentCaptor<String> emailed = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(anyString(), anyString(), emailed.capture());
        String raw = emailed.getValue();
        assertThat(user.getPasswordResetToken()).isNotEqualTo(raw);
        assertThat(user.getPasswordResetToken()).isEqualTo(AuthService.hashToken(raw));
        assertThat(user.getPasswordResetExpires()).isAfter(LocalDateTime.now());
    }

    @Test
    void resetPassword_withValidToken_changesPassword_clearsToken_revokesSessions() {
        String raw = "raw-token";
        user.setPasswordResetToken(AuthService.hashToken(raw));
        user.setPasswordResetExpires(LocalDateTime.now().plusMinutes(30));
        when(userRepository.findByPasswordResetToken(AuthService.hashToken(raw))).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("ENC");

        service.resetPassword(reset(raw, "NewPassw0rd!", "NewPassw0rd!"));

        assertThat(user.getPassword()).isEqualTo("ENC");
        assertThat(user.getPasswordResetToken()).isNull();
        verify(refreshTokenRepository).revokeAllUserTokens("u1");
    }

    @Test
    void resetPassword_withHashAsToken_isRejected() {
        // A leaked database value (the hash) must not work as the token.
        String raw = "raw-token";
        String hash = AuthService.hashToken(raw);
        user.setPasswordResetToken(hash);
        user.setPasswordResetExpires(LocalDateTime.now().plusMinutes(30));
        when(userRepository.findByPasswordResetToken(hash)).thenReturn(Optional.of(user));
        when(userRepository.findByPasswordResetToken(AuthService.hashToken(hash))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(reset(hash, "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPassword_expiredToken_isRejected() {
        String raw = "raw-token";
        user.setPasswordResetToken(AuthService.hashToken(raw));
        user.setPasswordResetExpires(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByPasswordResetToken(AuthService.hashToken(raw))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(reset(raw, "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(BusinessException.class);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void resetPassword_mismatchedConfirmation_isRejected() {
        assertThatThrownBy(() -> service.resetPassword(reset("t", "NewPassw0rd!", "different")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void hashToken_isDeterministic_andHex64() {
        assertThat(AuthService.hashToken("x")).isEqualTo(AuthService.hashToken("x")).hasSize(64);
        assertThat(AuthService.hashToken("x")).isNotEqualTo(AuthService.hashToken("y"));
    }
}
