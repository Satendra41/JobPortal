package com.jobportal.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form-only validation keeps persistence compatible with existing application history. */
public class ApplicationForm {
    private Long jobId;
    private String applicantName;
    private String applicantEmail;

    @NotBlank(message = "Enter a phone number so the employer can contact you.")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer.")
    @Pattern(regexp = "[0-9+() .-]{7,30}",
            message = "Enter a valid phone number using digits and common phone punctuation.")
    private String phone;

    @Size(max = 2000, message = "Cover letter must be 2,000 characters or fewer.")
    private String coverLetter;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
}
