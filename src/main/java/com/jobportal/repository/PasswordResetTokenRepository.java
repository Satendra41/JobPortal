package com.jobportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenDigest(String tokenDigest);

    @Query("select token.user.id from PasswordResetToken token where token.tokenDigest = :digest")
    Optional<Long> findUserIdByTokenDigest(@Param("digest") String digest);

    @Transactional
    @Modifying
    @Query("delete from PasswordResetToken token where token.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken token set token.consumed = true "
            + "where token.user.id = :userId and token.consumed = false")
    int consumeOutstandingByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.id = :id")
    Optional<PasswordResetToken> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenDigest = :digest")
    Optional<PasswordResetToken> findByTokenDigestForUpdate(@Param("digest") String digest);
}
