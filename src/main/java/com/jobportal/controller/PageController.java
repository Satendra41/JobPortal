package com.jobportal.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.jobportal.model.Profile;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.JobService;
import com.jobportal.services.ProfileService;
import com.jobportal.services.SavedJobService;
import com.jobportal.services.UserService;
import jakarta.validation.Valid;

import jakarta.servlet.http.HttpSession;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new com.jobportal.model.User());
        return "register";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("user", new com.jobportal.model.User());
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        User user = getJobSeeker(session);
        if (user == null) {
            return dashboardRedirect(session);
        }
        var applications = applicationService.getApplicationsForUser(user.getId());
        model.addAttribute("userName", user.getName());
        model.addAttribute("applicationCount", applications.size());
        model.addAttribute("interviewCount", applications.stream()
                .filter(application -> "INTERVIEW".equals(application.getStatus())).count());
        model.addAttribute("selectedCount", applications.stream()
                .filter(application -> "SELECTED".equals(application.getStatus())).count());
        model.addAttribute("savedJobCount", savedJobService.countForUser(user.getId()));
        return "dashboard";
    }

    private final JobService jobService;
    private final ProfileService profileService;
    private final UserService userService;
    private final ApplicationService applicationService;
    private final SavedJobService savedJobService;

    public PageController(JobService jobService, ProfileService profileService,
            UserService userService, ApplicationService applicationService, SavedJobService savedJobService) {
        this.jobService = jobService;
        this.profileService = profileService;
        this.userService = userService;
        this.applicationService = applicationService;
        this.savedJobService = savedJobService;
    }

    @GetMapping("/jobs")
    public String jobs(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String minSalary,
            @RequestParam(required = false) String maxSalary,
            @RequestParam(required = false) String sort,
            Model model) {
        String searchKeyword = normalize(keyword);
        String searchLocation = normalize(location);
        String searchCompany = normalize(company);
        String searchExperience = normalize(experience);
        String searchWorkMode = normalizeWorkMode(workMode);
        String searchJobType = normalize(jobType);
        String searchMinSalary = normalize(minSalary);
        String searchMaxSalary = normalize(maxSalary);
        String searchSort = normalizeSort(sort);

        model.addAttribute("jobs", jobService.searchPublicJobs(searchKeyword, searchLocation, searchCompany,
                searchExperience, searchWorkMode, searchJobType, searchMinSalary, searchMaxSalary, searchSort));
        model.addAttribute("keyword", searchKeyword);
        model.addAttribute("location", searchLocation);
        model.addAttribute("company", searchCompany);
        model.addAttribute("experience", searchExperience);
        model.addAttribute("workMode", searchWorkMode);
        model.addAttribute("jobType", searchJobType);
        model.addAttribute("minSalary", searchMinSalary);
        model.addAttribute("maxSalary", searchMaxSalary);
        model.addAttribute("sort", searchSort);
        model.addAttribute("hasFilters", !searchKeyword.isEmpty() || !searchLocation.isEmpty() || !searchCompany.isEmpty()
                || !searchExperience.isEmpty() || !searchJobType.isEmpty() || !searchMinSalary.isEmpty()
                || !searchMaxSalary.isEmpty() || !searchWorkMode.isEmpty());
        return "jobs";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeWorkMode(String workMode) {
        return switch (normalize(workMode).toLowerCase(Locale.ROOT)) {
            case "on-site" -> "On-site";
            case "hybrid" -> "Hybrid";
            case "remote" -> "Remote";
            default -> "";
        };
    }

    private String normalizeSort(String sort) {
        return switch (normalize(sort).toLowerCase(Locale.ROOT)) {
            case "oldest", "title" -> normalize(sort).toLowerCase(Locale.ROOT);
            default -> "newest";
        };
    }

    @GetMapping({ "/jobs/{id}", "/job/{id}" })
    public String jobDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        var job = jobService.getJobById(id);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/jobs";
        }
        model.addAttribute("job", job);
        User user = getJobSeeker(session);
        Boolean isJobSeeker = Boolean.FALSE;
        Boolean isJobSaved = Boolean.FALSE;
        if (user != null) {
            isJobSeeker = Boolean.TRUE;
            isJobSaved = Boolean.valueOf(savedJobService.isSaved(user.getId(), job.getId()));
        }
        model.addAttribute("isJobSeeker", isJobSeeker);
        model.addAttribute("isJobSaved", isJobSaved);
        return "job-details";
    }

    @GetMapping("/apply")
    public String apply() {
        return "redirect:/jobs";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        User user = getJobSeeker(session);
        if (user == null) {
            return session.getAttribute("loggedInUser") == null ? "redirect:/login" : "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        model.addAttribute("profile", profileService.getOrCreateProfile(user.getId()));
        return "profile";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam(value = "name", required = false) String name,
            @Valid @ModelAttribute("profile") Profile submittedProfile,
            BindingResult bindingResult,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = getJobSeeker(session);
        if (user == null) {
            return session.getAttribute("loggedInUser") == null ? "redirect:/login" : "redirect:/dashboard";
        }
        Profile profile = profileService.getOrCreateProfile(user.getId());
        if (name == null || name.isBlank() || name.length() > 160 || bindingResult.hasErrors()) {
            profile.setPhone(submittedProfile.getPhone());
            profile.setLocation(submittedProfile.getLocation());
            profile.setHeadline(submittedProfile.getHeadline());
            profile.setSkills(submittedProfile.getSkills());
            profile.setEducation(submittedProfile.getEducation());
            profile.setExperience(submittedProfile.getExperience());
            model.addAttribute("user", user);
            model.addAttribute("profileName", name);
            model.addAttribute("error", "Please correct the highlighted profile fields.");
            if (name == null || name.isBlank()) {
                model.addAttribute("nameError", "Name is required.");
            } else if (name.length() > 160) {
                model.addAttribute("nameError", "Name must be 160 characters or fewer.");
            }
            return "profile";
        }
        user.setName(name);
        profile.setUserId(user.getId());
        profile.setPhone(submittedProfile.getPhone());
        profile.setLocation(submittedProfile.getLocation());
        profile.setHeadline(submittedProfile.getHeadline());
        profile.setSkills(submittedProfile.getSkills());
        profile.setEducation(submittedProfile.getEducation());
        profile.setExperience(submittedProfile.getExperience());
        String oldResume = profile.getResumePath();
        try {
            if (resumeFile != null && !resumeFile.isEmpty()) {
                profile.setResumePath(profileService.storeResume(resumeFile));
            }
            userService.save(user);
            profileService.save(profile);
            if (oldResume != null && !oldResume.equals(profile.getResumePath())) {
                profileService.deleteResume(oldResume);
            }
            redirectAttributes.addFlashAttribute("message", "Profile saved successfully.");
        } catch (Exception ex) {
            profile.setResumePath(oldResume);
            redirectAttributes.addFlashAttribute("error", "Unable to save your profile or resume. Please check the form and selected file, then try again.");
        }
        return "redirect:/profile";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleProfileUploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Unable to save your profile or resume. Please check the form and selected file, then try again.");
        return "redirect:/profile";
    }

    private User getJobSeeker(HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        return loggedInUser instanceof User user && user.getRole() == UserRole.JOB_SEEKER ? user : null;
    }

    private String dashboardRedirect(HttpSession session) {
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

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }
}
