package com.jobportal.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.BindingResult;

import com.jobportal.model.Application;
import com.jobportal.model.ApplicationForm;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.JobService;
import com.jobportal.services.ProfileService;
import com.jobportal.services.SavedJobService;
import jakarta.validation.Valid;

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
        if (job == null || jobService.isDemoJobHiddenFromPublic(job)) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }

        if ("CLOSED".equals(job.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This job is no longer accepting applications.");
            return "redirect:/jobs/" + job.getId();
        }

        if (hasDeadlinePassed(job)) {
            redirectAttributes.addFlashAttribute("error", "The application deadline for this job has passed.");
            return "redirect:/jobs/" + job.getId();
        }

        if (applicationService.hasUserApplied(user.getId(), job.getId())) {
            model.addAttribute("alreadyAppliedMessage", "You have already applied for this job.");
            addJobDetailsModel(model, user, job);
            return "job-details";
        }

        ApplicationForm application = new ApplicationForm();
        application.setJobId(job.getId());
        application.setApplicantName(user.getName());
        application.setApplicantEmail(user.getEmail());
        var profile = profileService.getOrCreateProfile(user.getId());
        application.setPhone(profile.getPhone());

        model.addAttribute("job", job);
        model.addAttribute("application", application);
        return "apply";
    }

    @PostMapping("/applications/submit")
    public String submitApplication(@Valid @ModelAttribute("application") ApplicationForm submittedApplication,
            BindingResult bindingResult,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getRole() != UserRole.JOB_SEEKER) {
            return "redirect:/login";
        }

        Job job = submittedApplication.getJobId() == null
                ? null : jobService.getJobById(submittedApplication.getJobId());
        if (job == null || jobService.isDemoJobHiddenFromPublic(job)) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }

        if ("CLOSED".equals(job.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This job is no longer accepting applications.");
            return "redirect:/jobs/" + job.getId();
        }

        if (hasDeadlinePassed(job)) {
            redirectAttributes.addFlashAttribute("error", "The application deadline for this job has passed.");
            return "redirect:/jobs/" + job.getId();
        }

        if (applicationService.hasUserApplied(user.getId(), job.getId())) {
            model.addAttribute("alreadyAppliedMessage", "You have already applied for this job.");
            addJobDetailsModel(model, user, job);
            return "job-details";
        }

        if (bindingResult.hasErrors()) {
            submittedApplication.setJobId(job.getId());
            submittedApplication.setApplicantName(user.getName());
            submittedApplication.setApplicantEmail(user.getEmail());
            return showApplicationForm(model, job, submittedApplication, null);
        }

        Application application = new Application();
        application.setUserId(user.getId());
        application.setJobId(job.getId());
        application.setApplicantName(user.getName());
        application.setApplicantEmail(user.getEmail());
        application.setCoverLetter(submittedApplication.getCoverLetter());
        application.setPhone(submittedApplication.getPhone());
        var profile = profileService.getOrCreateProfile(user.getId());
        if (application.getPhone() == null || application.getPhone().isBlank()) {
            application.setPhone(profile.getPhone());
        }

        String applicationResumeFilename;
        try {
            applicationResumeFilename = profileService.storeApplicationResume(resumeFile);
        } catch (IllegalArgumentException ex) {
            return showApplicationForm(model, job, application, ex.getMessage());
        } catch (IOException ex) {
            return showApplicationForm(model, job, application,
                    "Unable to store the uploaded resume. Please try again.");
        }

        application.setResume(null);
        application.setApplicationResumeFilename(applicationResumeFilename);
        application.setStatus("APPLIED");
        application.setAppliedDate(LocalDate.now());

        try {
            applicationService.saveApplication(application);
        } catch (DataIntegrityViolationException ex) {
            cleanupApplicationResume(applicationResumeFilename, ex);
            if (applicationService.hasUserApplied(user.getId(), job.getId())) {
                model.addAttribute("alreadyAppliedMessage", "You have already applied for this job.");
                addJobDetailsModel(model, user, job);
                return "job-details";
            }
            throw ex;
        } catch (RuntimeException ex) {
            cleanupApplicationResume(applicationResumeFilename, ex);
            throw ex;
        }
        return "redirect:/applications/success";
    }

    private void cleanupApplicationResume(String resumeFilename, RuntimeException exception) {
        try {
            profileService.deleteResume(resumeFilename);
        } catch (IOException cleanupException) {
            exception.addSuppressed(cleanupException);
        }
    }

    private boolean hasDeadlinePassed(Job job) {
        LocalDate deadline = job.getApplicationDeadline();
        return deadline != null && deadline.isBefore(LocalDate.now());
    }

    private String showApplicationForm(Model model, Job job, Application application, String error) {
        ApplicationForm form = new ApplicationForm();
        form.setJobId(application.getJobId());
        form.setApplicantName(application.getApplicantName());
        form.setApplicantEmail(application.getApplicantEmail());
        form.setPhone(application.getPhone());
        form.setCoverLetter(application.getCoverLetter());
        return showApplicationForm(model, job, form, error);
    }

    private String showApplicationForm(Model model, Job job, ApplicationForm application, String error) {
        model.addAttribute("job", job);
        model.addAttribute("application", application);
        model.addAttribute("error", error);
        return "apply";
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
