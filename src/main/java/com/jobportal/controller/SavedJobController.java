package com.jobportal.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.Job;
import com.jobportal.model.SavedJob;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.JobService;
import com.jobportal.services.SavedJobService;

import jakarta.servlet.http.HttpSession;

@Controller
public class SavedJobController {

    private final JobService jobService;
    private final SavedJobService savedJobService;

    public SavedJobController(JobService jobService, SavedJobService savedJobService) {
        this.jobService = jobService;
        this.savedJobService = savedJobService;
    }

    @GetMapping("/saved-jobs")
    public String savedJobs(HttpSession session, Model model) {
        User user = getJobSeeker(session);
        if (user == null) {
            return unauthorizedRedirect(session);
        }

        List<SavedJob> savedJobs = savedJobService.getSavedJobsForUser(user.getId());
        Map<Long, Job> jobMap = new HashMap<>();
        savedJobs.forEach(savedJob -> {
            Job job = jobService.getJobById(savedJob.getJobId());
            if (job != null) {
                jobMap.put(job.getId(), job);
            }
        });
        model.addAttribute("savedJobs", savedJobs);
        model.addAttribute("jobMap", jobMap);
        return "saved-jobs";
    }

    @PostMapping("/jobs/{id}/save")
    public String saveJob(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getJobSeeker(session);
        if (user == null) {
            return unauthorizedRedirect(session);
        }
        Job job = jobService.getJobById(id);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }
        if (savedJobService.isSaved(user.getId(), job.getId())) {
            redirectAttributes.addFlashAttribute("message", "Job is already saved.");
        } else {
            savedJobService.save(user.getId(), job.getId());
            redirectAttributes.addFlashAttribute("message", "Job saved.");
        }
        return "redirect:/jobs/" + job.getId();
    }

    @PostMapping("/jobs/{id}/unsave")
    public String unsaveJob(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = getJobSeeker(session);
        if (user == null) {
            return unauthorizedRedirect(session);
        }
        if (savedJobService.remove(user.getId(), id)) {
            redirectAttributes.addFlashAttribute("message", "Job removed from saved jobs.");
        } else {
            redirectAttributes.addFlashAttribute("error", "That job is not in your saved jobs.");
        }
        return "redirect:/saved-jobs";
    }

    private User getJobSeeker(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        return loggedInUser instanceof User user && user.getRole() == UserRole.JOB_SEEKER ? user : null;
    }

    private String unauthorizedRedirect(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user)) {
            return "redirect:/login";
        }
        if (user.getRole() == UserRole.HR) {
            return "redirect:/hr/dashboard";
        }
        if (user.getRole() == UserRole.ADMIN) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/login";
    }
}
