package com.jobportal.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobportal.model.PasswordResetOutboxJob;

public interface PasswordResetOutboxJobRepository extends JpaRepository<PasswordResetOutboxJob, String> {

    @Query(value = """
            SELECT id FROM password_reset_email_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND locked_until <= :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<String> findCandidateIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            UPDATE password_reset_email_outbox
            SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                locked_until = :leaseUntil, updated_at = :now
            WHERE id = :id AND (
                (status = 'PENDING' AND next_attempt_at <= :now)
                OR (status = 'PROCESSING' AND locked_until <= :now)
            )
            """, nativeQuery = true)
    int claim(@Param("id") String id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
