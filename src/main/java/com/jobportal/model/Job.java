package com.jobportal.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Job title is required.")
    @Size(max = 180, message = "Job title must be 180 characters or fewer.")
    private String title;

    @NotBlank(message = "Company name is required.")
    @Size(max = 200, message = "Company name must be 200 characters or fewer.")
    private String company;

    @NotBlank(message = "Location is required.")
    @Size(max = 160, message = "Location must be 160 characters or fewer.")
    private String location;

    @NotBlank(message = "Job description is required.")
    @Size(max = 10000, message = "Job description must be 10,000 characters or fewer.")
    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 120, message = "Salary must be 120 characters or fewer.")
    private String salary;

    @Size(max = 2000, message = "Skills must be 2,000 characters or fewer.")
    @Column(length = 2000)
    private String skills;

    @NotBlank(message = "Employment type is required.")
    @Size(max = 80, message = "Employment type must be 80 characters or fewer.")
    private String jobType;

    @NotBlank(message = "Select a work mode.")
    @Pattern(regexp = "On-site|Hybrid|Remote", message = "Select On-site, Hybrid, or Remote.")
    private String workMode;

    @Column(name = "owner_id")
    private Long ownerId;

    @Size(max = 100, message = "Experience must be 100 characters or fewer.")
    private String experience;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    private String status = "ACTIVE";

    @Column(name = "posted_date")
    private LocalDate postedDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    public String getWorkMode() {
    return workMode;
}

public void setWorkMode(String workMode) {
    this.workMode = workMode;
}

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public String getStatus() {
        return status == null ? "ACTIVE" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
