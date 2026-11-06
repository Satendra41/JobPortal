package com.jobportal.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.services.PasswordResetClientAddressResolver;
import com.jobportal.services.PasswordResetRequestService;
import com.jobportal.services.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class PasswordResetController {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);
    private static final String GENERIC_REQUEST_MESSAGE =
            "If an account can receive a password reset link, one will be sent.";

    private final PasswordResetRequestService requestService;
    private final PasswordResetClientAddressResolver addressResolver;
    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetRequestService requestService,
            PasswordResetClientAddressResolver addressResolver, PasswordResetService passwordResetService) {
        this.requestService = requestService;
        this.addressResolver = addressResolver;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(@RequestParam(required = false) String submitted, Model model) {
        if ("1".equals(submitted)) model.addAttribute("message", GENERIC_REQUEST_MESSAGE);
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgotPassword(@RequestParam(required = false) String email,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        requestService.requestReset(email, addressResolver.resolve(request));
        redirectAttributes.addAttribute("submitted", "1");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token,
            Model model, HttpServletResponse response) {
        addResetSecurityHeaders(response);
        initializeResetModel(model);
        try {
            if (passwordResetService.isTokenRedeemable(token)) {
                model.addAttribute("resetToken", token);
            } else {
                model.addAttribute("invalidLink", true);
            }
        } catch (RuntimeException ex) {
            logger.warn("Password reset link validation failed.");
            model.addAttribute("validationUnavailable", true);
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String submitResetPassword(@RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirmPassword,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        addResetSecurityHeaders(response);
        initializeResetModel(model);
        if (!isRedeemableSafely(token, model)) {
            if (model.containsAttribute("validationUnavailable")) return "reset-password";
            model.addAttribute("invalidLink", true);
            return "reset-password";
        }
        if (password == null || password.isEmpty()) {
            model.addAttribute("resetToken", token);
            model.addAttribute("error", "Enter a new password.");
            return "reset-password";
        }
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            model.addAttribute("resetToken", token);
            model.addAttribute("error", "The passwords do not match.");
            return "reset-password";
        }

        try {
            passwordResetService.resetPassword(token, password);
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            return "redirect:/login?passwordReset=success";
        } catch (IllegalArgumentException ex) {
            if (!isRedeemableSafely(token, model)) {
                if (model.containsAttribute("validationUnavailable")) return "reset-password";
                model.addAttribute("invalidLink", true);
            } else {
                model.addAttribute("resetToken", token);
                model.addAttribute("error", ex.getMessage());
            }
            return "reset-password";
        } catch (RuntimeException ex) {
            logger.warn("Password reset could not be completed.");
            model.addAttribute("resetToken", token);
            model.addAttribute("error", "We could not update your password. Please try again.");
            return "reset-password";
        }
    }

    private void addResetSecurityHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }

    private boolean isRedeemableSafely(String token, Model model) {
        try {
            return passwordResetService.isTokenRedeemable(token);
        } catch (RuntimeException ex) {
            logger.warn("Password reset link validation failed.");
            model.addAttribute("validationUnavailable", true);
            return false;
        }
    }

    private void initializeResetModel(Model model) {
        model.addAttribute("invalidLink", false);
        model.addAttribute("validationUnavailable", false);
    }
}
