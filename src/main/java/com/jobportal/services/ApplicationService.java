package com.jobportal.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jobportal.model.Application;
import com.jobportal.repository.ApplicationRepository;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public Application saveApplication(Application application) {
        return applicationRepository.save(application);
    }

    public boolean hasUserApplied(Long userId, Long jobId) {
        return applicationRepository.existsByUserIdAndJobId(userId, jobId);
    }

    public List<Application> getApplicationsForUser(Long userId) {
        return applicationRepository.findByUserIdOrderByAppliedDateDesc(userId);
    }

    public List<Application> getApplicationsForJob(Long jobId) {
        return applicationRepository.findByJobIdOrderByAppliedDateDesc(jobId);
    }

    public long countApplicationsForJob(Long jobId) {
        return applicationRepository.countByJobId(jobId);
    }

    public Application getApplicationById(Long id) {
        return applicationRepository.findById(id).orElse(null);
    }

    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }
}
