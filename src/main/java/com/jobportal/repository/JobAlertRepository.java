package com.jobportal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobportal.model.JobAlert;

public interface JobAlertRepository extends JpaRepository<JobAlert, Long> {

    List<JobAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<JobAlert> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}