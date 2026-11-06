package com.jobportal.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.multipart.MultipartFile;

import com.jobportal.controller.PageController;
import com.jobportal.controller.ApplicationController;
import com.jobportal.controller.SavedJobController;
import com.jobportal.model.Application;
import com.jobportal.model.ApplicationForm;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.JobRepository;

class ProductionDemoVisibilityTests {

    @Test
    void localHomeAndSearchKeepDemoAndGenuineJobsVisible() {
        JobRepository repository = mock(JobRepository.class);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "local");
        environment.setActiveProfiles("local");
        Job demo = job(1L, "DEMO JOB — Java Developer", null);
        Job genuine = job(2L, "Backend Engineer", 45L);
        when(repository.findTop3ByStatusOrderByPostedDateDesc("ACTIVE")).thenReturn(List.of(demo, genuine));
        when(repository.findAll()).thenReturn(List.of(demo, genuine));

        JobService service = new JobService(repository, environment);

        assertThat(service.getRecentPublicJobs()).containsExactly(demo, genuine);
        assertThat(service.searchPublicJobs(null)).containsExactly(demo, genuine);
        verify(repository, never()).findTopPublicJobsExcludingDemo(any(), any());
    }

    @Test
    void productionHomeExcludesDemoBeforeApplyingRecentLimitAndKeepsGenuineHrJobs() {
        JobRepository repository = mock(JobRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        Job genuineHrJob = job(2L, "Backend Engineer", 45L);
        when(repository.findTopPublicJobsExcludingDemo(eq("ACTIVE"), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of(genuineHrJob));
        when(repository.findAll()).thenReturn(List.of(job(1L, "DEMO JOB — Java Developer", null), genuineHrJob));

        JobService service = new JobService(repository, environment);

        assertThat(service.getRecentPublicJobs()).containsExactly(genuineHrJob);
        assertThat(service.searchPublicJobs(null)).containsExactly(genuineHrJob);
        assertThat(service.isDemoJobHiddenFromPublic(job(1L, "DEMO JOB — Java Developer", null))).isTrue();
        assertThat(service.isDemoJobHiddenFromPublic(genuineHrJob)).isFalse();
        verify(repository, never()).findTop3ByStatusOrderByPostedDateDesc("ACTIVE");
    }

    @Test
    void productionDirectPublicDetailIsBlockedButAdminReviewRemainsAvailable() {
        JobRepository repository = mock(JobRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        Job demo = job(1L, "DEMO JOB — Java Developer", null);
        when(repository.findById(1L)).thenReturn(Optional.of(demo));
        JobService service = new JobService(repository, environment);
        PageController controller = new PageController(service, null, null, null, null, null);
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpSession publicSession = new MockHttpSession();

        assertThat(controller.jobDetails(1L, model, redirect, publicSession)).isEqualTo("redirect:/jobs");
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("Job not found.");

        User admin = new User();
        admin.setRole(UserRole.ADMIN);
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("loggedInUser", admin);
        assertThat(controller.jobDetails(1L, new ExtendedModelMap(), new RedirectAttributesModelMap(), adminSession))
                .isEqualTo("job-details");
    }

    @Test
    void productionDoesNotAllowNewApplicationsOrSavedReferencesToDemoJobs() {
        JobRepository repository = mock(JobRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        Job demo = job(1L, "DEMO JOB — Java Developer", null);
        when(repository.findById(1L)).thenReturn(Optional.of(demo));
        JobService service = new JobService(repository, environment);
        ApplicationService applications = mock(ApplicationService.class);
        ProfileService profiles = mock(ProfileService.class);
        SavedJobService savedJobs = mock(SavedJobService.class);
        ApplicationController applicationController = new ApplicationController(service, applications, profiles,
                savedJobs);
        SavedJobController savedJobController = new SavedJobController(service, savedJobs);
        User seeker = new User();
        seeker.setId(22L);
        seeker.setRole(UserRole.JOB_SEEKER);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", seeker);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertThat(applicationController.applyForJob(1L, new ExtendedModelMap(), session, redirect))
                .isEqualTo("redirect:/jobs");
        ApplicationForm submitted = new ApplicationForm();
        submitted.setJobId(1L);
        assertThat(applicationController.submitApplication(submitted, null, (MultipartFile) null, session,
                new ExtendedModelMap(), new RedirectAttributesModelMap())).isEqualTo("redirect:/jobs");
        assertThat(savedJobController.saveJob(1L, session, new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/jobs");
        verify(applications, never()).saveApplication(any());
        verify(savedJobs, never()).save(any(), any());
    }

    private Job job(Long id, String title, Long ownerId) {
        Job job = new Job();
        job.setId(id);
        job.setTitle(title);
        job.setCompany(ownerId == null ? "DEMO Example" : "Genuine Example");
        job.setLocation("Remote");
        job.setDescription("Role description");
        job.setSalary("10-15 LPA");
        job.setSkills("Java");
        job.setJobType("Full-time");
        job.setWorkMode("Remote");
        job.setOwnerId(ownerId);
        job.setPostedDate(LocalDate.now());
        job.setStatus("ACTIVE");
        return job;
    }
}
