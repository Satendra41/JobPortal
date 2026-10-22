package com.jobportal.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.jobportal.model.Job;
import com.jobportal.repository.JobRepository;

@Service
public class JobService {

    private static final Pattern SALARY_AMOUNT_PATTERN = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?");

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    /**
     * Returns the jobs that can be shown on the public jobs page. Closed jobs
     * remain available to their HR owner, but are not listed to visitors.
     */
    public List<Job> searchPublicJobs(String keyword) {
        return searchPublicJobs(keyword, null, null, null, null, null, null, null, "newest");
    }

    public List<Job> searchPublicJobs(String keyword, String location, String company, String experience,
            String jobType, String minSalary, String maxSalary, String sort) {
        return searchPublicJobs(keyword, location, company, experience, null, jobType, minSalary, maxSalary, sort);
    }

    public List<Job> searchPublicJobs(String keyword, String location, String company, String experience,
            String workMode, String jobType, String minSalary, String maxSalary, String sort) {
        return searchJobs(keyword, location, company, experience, "ACTIVE", workMode, jobType, minSalary, maxSalary,
                sort);
    }

    public List<Job> searchJobs(String keyword, String location, String company, String experience,
            String status, String jobType, String minSalary, String maxSalary, String sort) {
        return searchJobs(keyword, location, company, experience, status, null, jobType, minSalary, maxSalary, sort);
    }

    public List<Job> searchJobs(String keyword, String location, String company, String experience,
            String status, String workMode, String jobType, String minSalary, String maxSalary, String sort) {
        Stream<Job> jobs = location == null || location.isBlank()
                ? jobRepository.findAll().stream()
                : jobRepository.findByLocationContainingIgnoreCase(location.trim()).stream();
        jobs = filterContains(jobs, keyword, job -> job.getTitle(), job -> job.getCompany(),
                job -> job.getLocation(), job -> job.getDescription(), job -> job.getSkills());
        jobs = filterContains(jobs, company, Job::getCompany);
        jobs = filterContains(jobs, experience, Job::getExperience);
        jobs = filterEqualsIgnoreCase(jobs, status, Job::getStatus);
        jobs = filterEqualsIgnoreCase(jobs, workMode, Job::getWorkMode);
        jobs = filterContains(jobs, jobType, Job::getJobType);
        jobs = filterSalaryRange(jobs, minSalary, maxSalary);

        return switch (sort == null ? "newest" : sort.toLowerCase(Locale.ROOT)) {
            case "oldest" -> jobs.sorted((first, second) -> compareNullable(first.getPostedDate(), second.getPostedDate()))
                    .toList();
            case "title" -> jobs.sorted((first, second) -> compareNullable(
                    first.getTitle() == null ? "" : first.getTitle().toLowerCase(Locale.ROOT),
                    second.getTitle() == null ? "" : second.getTitle().toLowerCase(Locale.ROOT)))
                    .toList();
            default -> jobs.sorted((first, second) -> compareNullable(second.getPostedDate(), first.getPostedDate()))
                    .toList();
        };
    }

    @SafeVarargs
    private final Stream<Job> filterContains(Stream<Job> jobs, String value,
            java.util.function.Function<Job, String>... fields) {
        if (value == null || value.isBlank()) {
            return jobs;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return jobs.filter(job -> Stream.of(fields).map(field -> field.apply(job))
                .filter(Objects::nonNull)
                .map(field -> field.toLowerCase(Locale.ROOT))
                .anyMatch(field -> field.contains(normalized)));
    }

    private Stream<Job> filterEqualsIgnoreCase(Stream<Job> jobs, String value,
            java.util.function.Function<Job, String> field) {
        if (value == null || value.isBlank()) {
            return jobs;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return jobs.filter(job -> {
            String fieldValue = field.apply(job);
            return fieldValue != null && fieldValue.toLowerCase(Locale.ROOT).equals(normalized);
        });
    }

    private Stream<Job> filterSalaryRange(Stream<Job> jobs, String minSalary, String maxSalary) {
        BigDecimal minimum = parseAmount(minSalary);
        BigDecimal maximum = parseAmount(maxSalary);
        if (minimum == null && maximum == null) {
            return jobs;
        }
        return jobs.filter(job -> {
            BigDecimal amount = parseAmount(job.getSalary());
            return amount != null
                    && (minimum == null || amount.compareTo(minimum) >= 0)
                    && (maximum == null || amount.compareTo(maximum) <= 0);
        });
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = SALARY_AMOUNT_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group().replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private <T extends Comparable<? super T>> int compareNullable(T first, T second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return first.compareTo(second);
    }

    public Job getJobById(Long id) {
        return jobRepository.findById(id).orElse(null);
    }

    public Job saveJob(Job job) {
        return jobRepository.save(job);
    }

    public List<Job> getJobsForOwner(Long ownerId) {
        return jobRepository.findByOwnerIdOrderByPostedDateDesc(ownerId);
    }

    public long countJobsForOwner(Long ownerId) {
        return jobRepository.countByOwnerId(ownerId);
    }

    public long countActiveJobsForOwner(Long ownerId) {
        return jobRepository.countByOwnerIdAndStatus(ownerId, "ACTIVE");
    }

    public long countClosedJobsForOwner(Long ownerId) {
        return jobRepository.countByOwnerIdAndStatus(ownerId, "CLOSED");
    }

    public boolean isOwnedBy(Job job, Long ownerId) {
        return job != null && ownerId != null && ownerId.equals(job.getOwnerId());
    }

    public void deleteJob(Job job) {
        jobRepository.delete(job);
    }
}
