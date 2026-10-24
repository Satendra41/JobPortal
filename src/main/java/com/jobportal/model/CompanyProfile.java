package com.jobportal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "company_profiles")
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "company_name", nullable = false)
    @NotBlank(message = "Company name is required.")
    @Size(max = 255, message = "Company name must be 255 characters or fewer.")
    private String companyName;

    @Column(columnDefinition = "TEXT")
    @Size(max = 10000, message = "Company description must be 10,000 characters or fewer.")
    private String description;

    @Size(max = 180, message = "Location must be 180 characters or fewer.")
    private String location;

    @Size(max = 120, message = "Industry must be 120 characters or fewer.")
    private String industry;

    @Column(length = 2048)
    @Size(max = 2048, message = "Website URL must be 2,048 characters or fewer.")
    @Pattern(regexp = "(?i)^(?:https?://[^\\s/$.?#][^\\s]*)?$",
            message = "Enter a valid HTTP or HTTPS website URL.")
    private String website;

    @Column(name = "company_size")
    @Pattern(regexp = "^(?:|1-10 employees|11-50 employees|51-200 employees|201-500 employees|501-1000 employees|1000\\+ employees)$",
            message = "Select a company size from the list.")
    private String companySize;

    @Column(name = "founded_year")
    @Min(value = 1800, message = "Founded year must be 1800 or later.")
    @Max(value = 2100, message = "Founded year must be 2100 or earlier.")
    private Integer foundedYear;

    @Column(name = "contact_email")
    @Email(message = "Enter a valid contact email address.")
    @Size(max = 254, message = "Contact email must be 254 characters or fewer.")
    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    @Size(max = 2000, message = "Address must be 2,000 characters or fewer.")
    private String address;

    @Column(name = "logo_path")
    @Size(max = 255, message = "Logo path must be 255 characters or fewer.")
    private String logoPath;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public Integer getFoundedYear() {
        return foundedYear;
    }

    public void setFoundedYear(Integer foundedYear) {
        this.foundedYear = foundedYear;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }
}
