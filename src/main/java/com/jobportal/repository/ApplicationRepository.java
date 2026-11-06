package com.jobportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobportal.model.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserId(Long userId);

    List<Application> findByUserIdOrderByAppliedDateDesc(Long userId);

    List<Application> findByJobIdOrderByAppliedDateDesc(Long jobId);

    long countByJobId(Long jobId);
}
