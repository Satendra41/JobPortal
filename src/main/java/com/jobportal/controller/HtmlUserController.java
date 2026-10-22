package com.jobportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.UserService;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class HtmlUserController {

    private final UserService userService;

    public HtmlUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public String registerUserForm(
            @ModelAttribute User user,
            Model model,
            RedirectAttributes redirectAttributes) {

        String registrationResult = userService.registerNewUser(user);
        if ("success".equals(registrationResult)) {
            redirectAttributes.addFlashAttribute("message", "Registration successful! Please log in.");
            return "redirect:/login";
        }

        model.addAttribute("error", registrationResult);
        model.addAttribute("user", user);
        return "register";
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
        model.addAttribute("user", user);
        return "login";
    }
}
