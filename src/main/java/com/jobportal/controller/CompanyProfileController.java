package com.jobportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.CompanyProfile;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.CompanyProfileService;

import jakarta.servlet.http.HttpSession;

@Controller
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;

    public CompanyProfileController(CompanyProfileService companyProfileService) {
        this.companyProfileService = companyProfileService;
    }

    @GetMapping("/hr/company-profile")
    public String companyProfile(Model model, HttpSession session) {

        User user = getHrUser(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        CompanyProfile profile = companyProfileService
                .getProfileForUser(user.getId())
                .orElse(new CompanyProfile());

        model.addAttribute("companyProfile", profile);
        model.addAttribute("user", user);

        return "company-profile";
    }

    @PostMapping("/hr/company-profile")
    public String saveCompanyProfile(
            @ModelAttribute("companyProfile") CompanyProfile submittedProfile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = getHrUser(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        if (submittedProfile.getCompanyName() == null
                || submittedProfile.getCompanyName().isBlank()) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Company name cannot be blank.");

            return "redirect:/hr/company-profile";
        }

        try {
            companyProfileService.saveProfile(
                    submittedProfile,
                    user.getId());

            redirectAttributes.addFlashAttribute(
                    "message",
                    "Company profile saved successfully.");

        } catch (Exception ex) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Unable to save company profile. Please try again.");
        }

        return "redirect:/hr/company-profile";
    }

    @PostMapping("/hr/company-profile/delete")
    public String deleteCompanyProfile(
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = getHrUser(session);

        if (user == null) {
            return unauthorizedRedirect(session);
        }

        if (companyProfileService.deleteProfile(user.getId())) {

            redirectAttributes.addFlashAttribute(
                    "message",
                    "Company profile deleted successfully.");

        } else {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Company profile not found.");
        }

        return "redirect:/hr/company-profile";
    }

    private User getHrUser(HttpSession session) {

        Object loggedInUser = session.getAttribute("loggedInUser");

        return loggedInUser instanceof User user
                && user.getRole() == UserRole.HR
                ? user
                : null;
    }

    private String unauthorizedRedirect(HttpSession session) {

        Object loggedInUser = session.getAttribute("loggedInUser");

        if (!(loggedInUser instanceof User user)) {
            return "redirect:/login";
        }

        if (user.getRole() == UserRole.JOB_SEEKER) {
            return "redirect:/dashboard";
        }

        if (user.getRole() == UserRole.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        return "redirect:/login";
    }
}