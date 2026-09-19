package com.jobportal.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.JobAlert;
import com.jobportal.repository.JobAlertRepository;

@Service
public class JobAlertService {

    private final JobAlertRepository jobAlertRepository;

    public JobAlertService(JobAlertRepository jobAlertRepository) {
        this.jobAlertRepository = jobAlertRepository;
    }

    public JobAlert createAlert(JobAlert jobAlert, Long userId) {
        jobAlert.setUserId(userId);
        jobAlert.setEnabled(true);
        jobAlert.setCreatedAt(LocalDateTime.now());
        return jobAlertRepository.save(jobAlert);
    }

    public List<JobAlert> getAlertsForUser(Long userId) {
        return jobAlertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<JobAlert> getAlertForUser(Long id, Long userId) {
        return jobAlertRepository.findByIdAndUserId(id, userId);
    }

    public long countForUser(Long userId) {
        return jobAlertRepository.countByUserId(userId);
    }

    @Transactional
    public boolean deleteAlert(Long id, Long userId) {
        Optional<JobAlert> alert = jobAlertRepository.findByIdAndUserId(id, userId);

        if (alert.isEmpty()) {
            return false;
        }

        jobAlertRepository.deleteByIdAndUserId(id, userId);
        return true;
    }

    @Transactional
    public boolean toggleAlert(Long id, Long userId) {
        Optional<JobAlert> alertOptional = jobAlertRepository.findByIdAndUserId(id, userId);

        if (alertOptional.isEmpty()) {
            return false;
        }

        JobAlert alert = alertOptional.get();
        alert.setEnabled(!alert.isEnabled());
        jobAlertRepository.save(alert);

        return true;
    }
}