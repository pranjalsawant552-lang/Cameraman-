package com.cameraman.auth.repository;

import com.cameraman.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId")
    void revokeAllUserTokens(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold OR rt.revoked = true")
    void deleteExpiredAndRevokedTokens(@Param("threshold") LocalDateTime threshold);

    long countByUserIdAndRevokedFalse(String userId);
}
