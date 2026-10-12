package com.cameraman.auth.service;

import com.cameraman.auth.repository.OtpRepository;
import com.cameraman.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCleanupScheduler {

    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Clean expired OTPs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void cleanExpiredOtps() {
        try {
            otpRepository.deleteExpiredOtps(LocalDateTime.now().minusMinutes(30));
            log.debug("Expired OTPs cleaned");
        } catch (Exception e) {
            log.error("Error cleaning expired OTPs: {}", e.getMessage());
        }
    }

    /**
     * Clean expired and revoked refresh tokens every 6 hours
     */
    @Scheduled(fixedRate = 21600000)
    @Transactional
    public void cleanExpiredTokens() {
        try {
            refreshTokenRepository.deleteExpiredAndRevokedTokens(LocalDateTime.now());
            log.debug("Expired refresh tokens cleaned");
        } catch (Exception e) {
            log.error("Error cleaning expired tokens: {}", e.getMessage());
        }
    }
}
