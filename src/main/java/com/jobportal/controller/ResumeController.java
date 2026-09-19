package com.jobportal.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.jobportal.model.Application;
import com.jobportal.model.Profile;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.JobService;
import com.jobportal.services.ProfileService;

import jakarta.servlet.http.HttpSession;

@Controller
public class ResumeController {

    private final ProfileService profileService;
    private final ApplicationService applicationService;
    private final JobService jobService;

    public ResumeController(ProfileService profileService, ApplicationService applicationService, JobService jobService) {
        this.profileService = profileService;
        this.applicationService = applicationService;
        this.jobService = jobService;
    }

    @GetMapping("/profile/resume")
        public ResponseEntity<InputStreamResource> ownResume(@RequestParam(defaultValue = "false") boolean download,
            HttpSession session) throws IOException {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getRole() != UserRole.JOB_SEEKER) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/login").build();
        }
        return serve(profileService.getOrCreateProfile(user.getId()), "resume.pdf", download);
    }

    @GetMapping("/hr/applications/{id}/resume")
        public ResponseEntity<InputStreamResource> applicantResume(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean download, HttpSession session) throws IOException {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User hr) || hr.getRole() != UserRole.HR) {
            return ResponseEntity.status(403).build();
        }
        Application application = applicationService.getApplicationById(id);
        if (application == null || !jobService.isOwnedBy(jobService.getJobById(application.getJobId()), hr.getId())) {
            return ResponseEntity.status(403).build();
        }
        Profile profile = profileService.getOrCreateProfile(application.getUserId());
        return serve(profile, "resume.pdf", download);
    }

    private ResponseEntity<InputStreamResource> serve(Profile profile, String filename, boolean download) throws IOException {
        Path path = profileService.getResumePath(profile);
        if (path == null || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + filename + "\"")
                .contentLength(Files.size(path))
                .body(new InputStreamResource(Files.newInputStream(path)));
    }
}