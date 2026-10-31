package com.jobportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.config.OAuthClientRegistrationRepository;
import com.jobportal.services.UserService;
import com.jobportal.services.RegistrationVerificationService;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class HtmlUserController {

    private final UserService userService;
    private final OAuthClientRegistrationRepository oauthClientRegistrationRepository;
    private final RegistrationVerificationService registrationVerificationService;

    public HtmlUserController(UserService userService,
            OAuthClientRegistrationRepository oauthClientRegistrationRepository,
            RegistrationVerificationService registrationVerificationService) {
        this.userService = userService;
        this.oauthClientRegistrationRepository = oauthClientRegistrationRepository;
        this.registrationVerificationService = registrationVerificationService;
    }

    @PostMapping("/register")
    public String registerUserForm(
            @ModelAttribute User user,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            registrationVerificationService.startRegistration(user.getName(), user.getEmail(), user.getPassword());
            redirectAttributes.addAttribute("email", user.getEmail().trim());
            return "redirect:/verify-email";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            User safeUser = new User();
            safeUser.setName(user.getName());
            safeUser.setEmail(user.getEmail());
            model.addAttribute("user", safeUser);
            addOAuthAvailability(model);
            return "register";
        }
    }

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam String email,
            @RequestParam String otp, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            registrationVerificationService.verify(email, otp);
            redirectAttributes.addFlashAttribute("message", "Your email is verified. You can now sign in.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("email", email);
            model.addAttribute("error", ex.getMessage());
            return "verify-email";
        }
    }

    @PostMapping("/verify-email/resend")
    public String resendVerificationEmail(@RequestParam String email,
            RedirectAttributes redirectAttributes) {
        try {
            registrationVerificationService.resendOtp(email);
            redirectAttributes.addAttribute("email", email);
            redirectAttributes.addFlashAttribute("message",
                    "If this address has an eligible pending sign-up, it will receive a verification code.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addAttribute("email", email);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/verify-email";
    }

    @PostMapping("/login")
    public String loginUserForm(
            @ModelAttribute User user,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session,
            HttpServletRequest request) {

        User authenticatedUser = userService.authenticateUser(user.getEmail(), user.getPassword());
        if (authenticatedUser != null) {
            request.changeSessionId();
            authenticatedUser.setPassword(null);
            session.setAttribute("loggedInUser", authenticatedUser);
            redirectAttributes.addFlashAttribute("message", "Login successful!");
            if (authenticatedUser.getRole() == UserRole.HR) {
                return "redirect:/hr/dashboard";
            }
            if (authenticatedUser.getRole() == UserRole.ADMIN) {
                return "redirect:/admin/dashboard";
            }
            return "redirect:/dashboard";
        }

        model.addAttribute("error", "Invalid email or password");
        User safeUser = new User();
        safeUser.setEmail(user.getEmail());
        model.addAttribute("user", safeUser);
        addOAuthAvailability(model);
        return "login";
    }

    private void addOAuthAvailability(Model model) {
        model.addAttribute("googleOAuthEnabled", oauthClientRegistrationRepository.isConfigured("google"));
        model.addAttribute("githubOAuthEnabled", oauthClientRegistrationRepository.isConfigured("github"));
    }
}
