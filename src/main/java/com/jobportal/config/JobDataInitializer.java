package com.jobportal.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.jobportal.model.Job;
import com.jobportal.repository.JobRepository;

@Component
public class JobDataInitializer implements ApplicationRunner {

    private final JobRepository jobRepository;

    public JobDataInitializer(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jobRepository.count() > 0) {
            return;
        }

        List<Job> sampleJobs = List.of(
            createJob("Java Developer", "ABC Technologies", "Dehradun", "Build backend services and APIs using Java and Spring Boot.", "₹6 LPA", "Java, Spring Boot, MySQL", "Full-time", null, LocalDate.now().minusDays(2)),
            createJob("Frontend Developer", "Tech Solutions", "Remote", "Create responsive frontend experiences using HTML, CSS, and JavaScript.", "₹5 LPA", "HTML, CSS, JavaScript", "Remote", null, LocalDate.now().minusDays(5)),
            createJob("Backend Developer", "XYZ Software", "Bangalore", "Develop REST APIs and backend systems with Java, Spring Boot, and database integrations.", "₹8 LPA", "Java, Spring Boot, REST API", "Full-time", "2-4 years", LocalDate.now().minusDays(3)),
            createJob("Full Stack Developer", "Startup India", "Delhi", "Work across frontend and backend stacks using Java, JavaScript, and MySQL.", "₹7 LPA", "Java, JavaScript, MySQL", "Full-time", null, LocalDate.now().minusDays(7))
        );

        jobRepository.saveAll(sampleJobs);
    }

    private Job createJob(String title, String company, String location, String description, String salary, String skills,
            String jobType, String experience, LocalDate postedDate) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setDescription(description);
        job.setSalary(salary);
        job.setSkills(skills);
        job.setJobType(jobType);
        job.setExperience(experience);
        job.setPostedDate(postedDate);
        return job;
    }
}
