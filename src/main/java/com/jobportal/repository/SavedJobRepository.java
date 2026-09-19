package com.jobportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobportal.model.SavedJob;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    Optional<SavedJob> findByUserIdAndJobId(Long userId, Long jobId);

    List<SavedJob> findByUserIdOrderBySavedAtDesc(Long userId);

    long countByUserId(Long userId);

    void deleteByUserIdAndJobId(Long userId, Long jobId);
}
