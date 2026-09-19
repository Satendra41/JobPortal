package com.jobportal.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.SavedJob;
import com.jobportal.repository.SavedJobRepository;

@Service
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;

    public SavedJobService(SavedJobRepository savedJobRepository) {
        this.savedJobRepository = savedJobRepository;
    }

    public boolean isSaved(Long userId, Long jobId) {
        return userId != null && jobId != null && savedJobRepository.existsByUserIdAndJobId(userId, jobId);
    }

    public SavedJob save(Long userId, Long jobId) {
        if (isSaved(userId, jobId)) {
            return savedJobRepository.findByUserIdAndJobId(userId, jobId).orElseThrow();
        }
        SavedJob savedJob = new SavedJob();
        savedJob.setUserId(userId);
        savedJob.setJobId(jobId);
        savedJob.setSavedAt(LocalDateTime.now());
        return savedJobRepository.save(savedJob);
    }

    @Transactional
    public boolean remove(Long userId, Long jobId) {
        if (!isSaved(userId, jobId)) {
            return false;
        }
        savedJobRepository.deleteByUserIdAndJobId(userId, jobId);
        return true;
    }

    public List<SavedJob> getSavedJobsForUser(Long userId) {
        return savedJobRepository.findByUserIdOrderBySavedAtDesc(userId);
    }

    public long countForUser(Long userId) {
        return savedJobRepository.countByUserId(userId);
    }

    public Set<Long> getSavedJobIds(Long userId) {
        return getSavedJobsForUser(userId).stream().map(SavedJob::getJobId).collect(Collectors.toSet());
    }
}
