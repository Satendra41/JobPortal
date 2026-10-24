package com.jobportal.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.JobAlert;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.JobAlertService;

import jakarta.servlet.http.HttpSession;

@Controller
public class JobAlertController {

    private final JobAlertService jobAlertService;

    public JobAlertController(JobAlertService jobAlertService) {
        this.jobAlertService = jobAlertService;
    }

    @GetMapping("/job-alerts")
    public String jobAlerts(HttpSession session, Model model) {
        User user = getJobSeeker(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        List<JobAlert> alerts = jobAlertService.getAlertsForUser(user.getId());

        model.addAttribute("alerts", alerts);
        model.addAttribute("newAlert", new JobAlert());

        return "job-alerts";
    }

    @PostMapping("/job-alerts")
    public String createAlert(
            @ModelAttribute("newAlert") JobAlert submittedAlert,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = getJobSeeker(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        jobAlertService.createAlert(submittedAlert, user.getId());

        redirectAttributes.addFlashAttribute(
                "message",
                "Job alert created successfully.");

        return "redirect:/job-alerts";
    }

    @PostMapping("/job-alerts/{id}/toggle")
    public String toggleAlert(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = getJobSeeker(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        if (jobAlertService.toggleAlert(id, user.getId())) {
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Job alert status updated.");
        } else {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Job alert not found.");
        }

        return "redirect:/job-alerts";
    }

    @PostMapping("/job-alerts/{id}/delete")
    public String deleteAlert(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = getJobSeeker(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        if (jobAlertService.deleteAlert(id, user.getId())) {
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Job alert deleted.");
        } else {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Job alert not found.");
        }

        return "redirect:/job-alerts";
    }

    private User getJobSeeker(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");

        return loggedInUser instanceof User user
                && user.getRole() == UserRole.JOB_SEEKER
                        ? user
                        : null;
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
