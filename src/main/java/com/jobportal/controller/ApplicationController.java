package com.jobportal.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.Application;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.JobService;
import com.jobportal.services.ProfileService;
import com.jobportal.services.SavedJobService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class ApplicationController {

    private final JobService jobService;
    private final ApplicationService applicationService;
    private final ProfileService profileService;
    private final SavedJobService savedJobService;

    public ApplicationController(JobService jobService, ApplicationService applicationService, ProfileService profileService,
            SavedJobService savedJobService) {
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.profileService = profileService;
        this.savedJobService = savedJobService;
    }

    @GetMapping("/jobs/{id}/apply")
    public String applyForJob(@PathVariable Long id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getRole() != UserRole.JOB_SEEKER) {
            return "redirect:/login";
        }

        Job job = jobService.getJobById(id);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }

        if ("CLOSED".equals(job.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This job is no longer accepting applications.");
            return "redirect:/jobs/" + job.getId();
        }

        if (applicationService.hasUserApplied(user.getId(), job.getId())) {
            model.addAttribute("alreadyAppliedMessage", "You have already applied for this job.");
            addJobDetailsModel(model, user, job);
            return "job-details";
        }

        Application application = new Application();
        application.setJobId(job.getId());
        application.setUserId(user.getId());
        application.setApplicantName(user.getName());
        application.setApplicantEmail(user.getEmail());
        var profile = profileService.getOrCreateProfile(user.getId());
        application.setPhone(profile.getPhone());
        application.setResume(profile.getResumePath());
        application.setStatus("APPLIED");
        application.setAppliedDate(LocalDate.now());

        model.addAttribute("job", job);
        model.addAttribute("application", application);
        return "apply";
    }

    @PostMapping("/applications/submit")
    public String submitApplication(@ModelAttribute Application application, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getRole() != UserRole.JOB_SEEKER) {
            return "redirect:/login";
        }

        Job job = jobService.getJobById(application.getJobId());
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }

        if ("CLOSED".equals(job.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This job is no longer accepting applications.");
            return "redirect:/jobs/" + job.getId();
        }

        if (applicationService.hasUserApplied(user.getId(), job.getId())) {
            model.addAttribute("alreadyAppliedMessage", "You have already applied for this job.");
            addJobDetailsModel(model, user, job);
            return "job-details";
        }

        application.setUserId(user.getId());
        application.setApplicantName(user.getName());
        application.setApplicantEmail(user.getEmail());
        var profile = profileService.getOrCreateProfile(user.getId());
        if (application.getPhone() == null || application.getPhone().isBlank()) {
            application.setPhone(profile.getPhone());
        }
        if (application.getResume() == null || application.getResume().isBlank()) {
            application.setResume(profile.getResumePath());
        }
        application.setStatus("APPLIED");
        application.setAppliedDate(LocalDate.now());

        applicationService.saveApplication(application);
        return "redirect:/applications/success";
    }

    @GetMapping("/applications/success")
    public String applicationSuccess() {
        return "application-success";
    }

    @GetMapping("/applications")
    public String myApplications(HttpSession session, Model model) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getRole() != UserRole.JOB_SEEKER) {
            return "redirect:/login";
        }

        List<Application> applications = applicationService.getApplicationsForUser(user.getId());
        Map<Long, Job> jobMap = new HashMap<>();
        applications.stream()
                .map(Application::getJobId)
                .distinct()
                .forEach(jobId -> {
                    Job job = jobService.getJobById(jobId);
                    if (job != null) {
                        jobMap.put(jobId, job);
                    }
                });

        model.addAttribute("applications", applications);
        model.addAttribute("jobMap", jobMap);
        return "applications";
    }

    private void addJobDetailsModel(Model model, User user, Job job) {
        model.addAttribute("job", job);
        boolean isJobSeeker = user.getRole() == com.jobportal.model.UserRole.JOB_SEEKER;
        model.addAttribute("isJobSeeker", isJobSeeker);
        model.addAttribute("isJobSaved", isJobSeeker && savedJobService.isSaved(user.getId(), job.getId()));
    }
}
