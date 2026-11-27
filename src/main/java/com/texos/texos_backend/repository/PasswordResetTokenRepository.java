package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
            String tokenHash, LocalDateTime now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.ownerId = :ownerId and t.usedAt is null")
    int deleteUnusedByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM password_reset_tokens
            WHERE expires_at <= :cutoff
            LIMIT 500
            """, nativeQuery = true)
    int deleteLongExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}
