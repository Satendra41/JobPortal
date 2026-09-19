package com.jobportal.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.Application;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.JobService;
import com.jobportal.services.ProfileService;

import jakarta.servlet.http.HttpSession;

@Controller
public class HrController {

    private static final List<String> APPLICATION_STATUSES = Arrays.asList(
            "APPLIED", "SHORTLISTED", "REJECTED", "INTERVIEW", "SELECTED");

    private final JobService jobService;
    private final ApplicationService applicationService;
    private final ProfileService profileService;

    public HrController(JobService jobService, ApplicationService applicationService, ProfileService profileService) {
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.profileService = profileService;
    }

    @GetMapping("/hr/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }

        List<Job> jobs = jobService.getJobsForOwner(user.getId());
        Map<Long, Long> applicationCounts = applicationCounts(jobs);

        model.addAttribute("userName", user.getName());
        model.addAttribute("jobs", jobs);
        model.addAttribute("applicationCounts", applicationCounts);
        model.addAttribute("totalJobs", jobs.size());
        model.addAttribute("activeJobs", jobs.stream().filter(job -> "ACTIVE".equals(job.getStatus())).count());
        model.addAttribute("closedJobs", jobs.stream().filter(job -> "CLOSED".equals(job.getStatus())).count());
        model.addAttribute("totalApplications", applicationCounts.values().stream().mapToLong(Long::longValue).sum());
        return "hr-dashboard";
    }

    @GetMapping("/hr/jobs")
    public String myJobs(HttpSession session, Model model) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        List<Job> jobs = jobService.getJobsForOwner(user.getId());
        model.addAttribute("jobs", jobs);
        model.addAttribute("applicationCounts", applicationCounts(jobs));
        return "hr-jobs";
    }

    @GetMapping("/hr/jobs/new")
    public String newJob(HttpSession session, Model model) {
        if (getHrUser(session) == null) {
            return redirectForUnauthorized(session);
        }
        model.addAttribute("job", new Job());
        model.addAttribute("formTitle", "Post a new job");
        model.addAttribute("formAction", "/hr/jobs");
        return "hr-job-form";
    }

    @PostMapping("/hr/jobs")
    public String createJob(@ModelAttribute Job job, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        job.setId(null);
        job.setOwnerId(user.getId());
        job.setPostedDate(LocalDate.now());
        job.setStatus("ACTIVE");
        jobService.saveJob(job);
        redirectAttributes.addFlashAttribute("message", "Job posted successfully.");
        return "redirect:/hr/dashboard";
    }

    @GetMapping("/hr/jobs/{id}/edit")
    public String editJob(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Job job = jobService.getJobById(id);
        if (!jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot edit this job.");
            return "redirect:/hr/dashboard";
        }
        model.addAttribute("job", job);
        model.addAttribute("formTitle", "Edit job");
        model.addAttribute("formAction", "/hr/jobs/" + id + "/edit");
        return "hr-job-form";
    }

    @PostMapping("/hr/jobs/{id}/edit")
    public String updateJob(@PathVariable Long id, @ModelAttribute Job submittedJob, HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Job job = jobService.getJobById(id);
        if (!jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot edit this job.");
            return "redirect:/hr/dashboard";
        }
        copyEditableFields(submittedJob, job);
        jobService.saveJob(job);
        redirectAttributes.addFlashAttribute("message", "Job updated successfully.");
        return "redirect:/hr/dashboard";
    }

    @PostMapping("/hr/jobs/{id}/close")
    public String closeJob(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Job job = jobService.getJobById(id);
        if (!jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot close this job.");
            return "redirect:/hr/dashboard";
        }
        job.setStatus("CLOSED");
        jobService.saveJob(job);
        redirectAttributes.addFlashAttribute("message", "Job closed successfully.");
        return "redirect:/hr/dashboard";
    }

    @GetMapping("/hr/jobs/{id}/applications")
    public String applications(@PathVariable Long id, HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Job job = jobService.getJobById(id);
        if (!jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot view applicants for this job.");
            return "redirect:/hr/dashboard";
        }
        model.addAttribute("job", job);
        model.addAttribute("applications", applicationService.getApplicationsForJob(id));
        return "hr-applications";
    }

    @GetMapping("/hr/applications/{id}")
    public String applicationDetails(@PathVariable Long id, HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Application application = applicationService.getApplicationById(id);
        Job job = application == null ? null : jobService.getJobById(application.getJobId());
        if (application == null || !jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot view this applicant.");
            return "redirect:/hr/dashboard";
        }
        model.addAttribute("jobApplication", application);
        model.addAttribute("job", job);
        model.addAttribute("applicantProfile", profileService.getOrCreateProfile(application.getUserId()));
        model.addAttribute("statuses", APPLICATION_STATUSES);
        return "hr-application-details";
    }

    @PostMapping("/hr/applications/{id}/status")
    public String updateApplicationStatus(@PathVariable Long id, @RequestParam String status,
            HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getHrUser(session);
        if (user == null) {
            return redirectForUnauthorized(session);
        }
        Application application = applicationService.getApplicationById(id);
        Job job = application == null ? null : jobService.getJobById(application.getJobId());
        if (application == null || !jobService.isOwnedBy(job, user.getId())) {
            redirectAttributes.addFlashAttribute("error", "You cannot update this application.");
            return "redirect:/hr/dashboard";
        }
        if (!APPLICATION_STATUSES.contains(status)) {
            redirectAttributes.addFlashAttribute("error", "Invalid application status.");
            return "redirect:/hr/applications/" + id;
        }
        application.setStatus(status);
        applicationService.saveApplication(application);
        redirectAttributes.addFlashAttribute("message", "Application status updated.");
        return "redirect:/hr/applications/" + id;
    }

    private Map<Long, Long> applicationCounts(List<Job> jobs) {
        Map<Long, Long> counts = new HashMap<>();
        for (Job job : jobs) {
            counts.put(job.getId(), applicationService.countApplicationsForJob(job.getId()));
        }
        return counts;
    }

    private void copyEditableFields(Job source, Job target) {
        target.setTitle(source.getTitle());
        target.setCompany(source.getCompany());
        target.setLocation(source.getLocation());
        target.setJobType(source.getJobType());
        target.setWorkMode(source.getWorkMode());
        target.setSalary(source.getSalary());
        target.setExperience(source.getExperience());
        target.setSkills(source.getSkills());
        target.setDescription(source.getDescription());
        target.setApplicationDeadline(source.getApplicationDeadline());
    }

    private User getHrUser(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (loggedInUser instanceof User user && user.getRole() == UserRole.HR) {
            return user;
        }
        return null;
    }

    private String redirectForUnauthorized(HttpSession session) {
        return session.getAttribute("loggedInUser") == null ? "redirect:/login" : "redirect:/dashboard";
    }
}
