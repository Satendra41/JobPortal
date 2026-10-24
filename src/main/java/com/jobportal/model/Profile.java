package com.jobportal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    @Size(max = 30, message = "Phone number must be 30 characters or fewer.")
    @Pattern(regexp = "^(?:[0-9+() .-]{7,30})?$", message = "Enter a valid phone number using digits and common phone punctuation.")
    private String phone;

    @Size(max = 120, message = "Location must be 120 characters or fewer.")
    private String location;

    @Size(max = 160, message = "Headline must be 160 characters or fewer.")
    private String headline;

    @Size(max = 1000, message = "Skills must be 1,000 characters or fewer.")
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String skills;

    @Size(max = 1000, message = "Education must be 1,000 characters or fewer.")
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String education;

    @Size(max = 2000, message = "Experience must be 2,000 characters or fewer.")
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String experience;
    private String resumePath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getResumePath() { return resumePath; }
    public void setResumePath(String resumePath) { this.resumePath = resumePath; }
}
