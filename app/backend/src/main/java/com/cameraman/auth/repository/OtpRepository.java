package com.cameraman.auth.repository;

import com.cameraman.auth.domain.OtpPurpose;
import com.cameraman.auth.domain.OtpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpRecord, String> {
    Optional<OtpRecord> findTopByIdentifierAndPurposeAndUsedFalseOrderByCreatedAtDesc(
        String identifier, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpRecord o SET o.used = true WHERE o.identifier = :identifier AND o.purpose = :purpose")
    void invalidateAllForIdentifier(@Param("identifier") String identifier, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM OtpRecord o WHERE o.expiresAt < :threshold")
    void deleteExpiredOtps(@Param("threshold") LocalDateTime threshold);

    long countByIdentifierAndPurposeAndCreatedAtAfter(String identifier, OtpPurpose purpose, LocalDateTime after);
}
