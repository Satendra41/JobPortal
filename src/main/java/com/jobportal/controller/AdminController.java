package com.jobportal.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.jobportal.services.UserService;

import jakarta.servlet.http.HttpSession;

@Controller
public class AdminController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;

    public AdminController(UserService userService, JobService jobService, ApplicationService applicationService) {
        this.userService = userService;
        this.jobService = jobService;
        this.applicationService = applicationService;
    }
    @GetMapping("/admin")
public String adminHome(HttpSession session) {
    if (!isAdmin(session)) {
        return unauthorizedRedirect(session);
    }
    return "redirect:/admin/dashboard";
}
   
    @GetMapping("/admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        List<User> users = userService.getAllUsers();
        List<Job> jobs = jobService.getAllJobs();
        List<Application> applications = applicationService.getAllApplications();
        addDashboardData(model, users, jobs, applications);
        return "admin-dashboard";
    }

    @GetMapping("/admin/users")
    public String users(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        model.addAttribute("users", userService.getAllUsers());
        return "admin-users";
    }

    @GetMapping("/admin/users/{id}")
    public String userDetails(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        User user = userService.getUserById(id);
        if (user == null) {
            return "redirect:/admin/users";
        }
        model.addAttribute("user", user);
        return "admin-user-details";
    }

    @PostMapping("/admin/users/{id}/role")
    public String updateUserRole(@PathVariable Long id, @RequestParam UserRole role, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        if (userService.updateRoleForAdmin(id, role)) {
            redirectAttributes.addFlashAttribute("message", "User role updated. The user must log out and log back in.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Only Job Seeker and HR roles can be managed.");
        }
        return "redirect:/admin/users/" + id;
    }

    @GetMapping("/admin/jobs")
    public String jobs(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        List<User> users = userService.getAllUsers();
        Map<Long, User> userMap = users.stream().filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));
        model.addAttribute("jobs", jobService.getAllJobs());
        model.addAttribute("userMap", userMap);
        return "admin-jobs";
    }

    @GetMapping("/admin/applications")
    public String applications(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return unauthorizedRedirect(session);
        }
        List<User> users = userService.getAllUsers();
        List<Job> jobs = jobService.getAllJobs();
        model.addAttribute("applications", applicationService.getAllApplications());
        model.addAttribute("userMap", users.stream().filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first)));
        model.addAttribute("jobMap", jobs.stream().filter(job -> job.getId() != null)
                .collect(Collectors.toMap(Job::getId, job -> job, (first, second) -> first)));
        return "admin-applications";
    }

    private void addDashboardData(Model model, List<User> users, List<Job> jobs, List<Application> applications) {
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("totalJobSeekers", users.stream().filter(user -> user.getRole() == UserRole.JOB_SEEKER).count());
        model.addAttribute("totalHrUsers", users.stream().filter(user -> user.getRole() == UserRole.HR).count());
        model.addAttribute("totalJobs", jobs.size());
        model.addAttribute("activeJobs", jobs.stream().filter(job -> "ACTIVE".equals(job.getStatus())).count());
        model.addAttribute("closedJobs", jobs.stream().filter(job -> "CLOSED".equals(job.getStatus())).count());
        model.addAttribute("totalApplications", applications.size());
        model.addAttribute("users", recentUsers(users));
        model.addAttribute("jobs", recentJobs(jobs));
        model.addAttribute("applications", recentApplications(applications));
        model.addAttribute("userMap", userMap(users));
        model.addAttribute("jobMap", jobMap(jobs));
    }

    private List<User> recentUsers(List<User> users) {
        return users.stream().sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).toList();
    }

    private List<Job> recentJobs(List<Job> jobs) {
        return jobs.stream().sorted(Comparator.comparing(Job::getPostedDate,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).toList();
    }

    private List<Application> recentApplications(List<Application> applications) {
        return applications.stream().sorted(Comparator.comparing(Application::getAppliedDate,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).toList();
    }

    private Map<Long, User> userMap(List<User> users) {
        return users.stream().filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));
    }

    private Map<Long, Job> jobMap(List<Job> jobs) {
        return jobs.stream().filter(job -> job.getId() != null)
                .collect(Collectors.toMap(Job::getId, job -> job, (first, second) -> first));
    }

    private boolean isAdmin(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        return loggedInUser instanceof User user && user.getRole() == UserRole.ADMIN;
    }

    private String unauthorizedRedirect(HttpSession session) {
        return session.getAttribute("loggedInUser") == null ? "redirect:/login" : "redirect:/dashboard";
    }
}
