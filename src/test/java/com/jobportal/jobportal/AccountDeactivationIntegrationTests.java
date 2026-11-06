package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.Application;
import com.jobportal.model.CompanyProfile;
import com.jobportal.model.Job;
import com.jobportal.model.JobAlert;
import com.jobportal.model.Profile;
import com.jobportal.model.SavedJob;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.CompanyProfileRepository;
import com.jobportal.repository.JobAlertRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.repository.SavedJobRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.OAuthAccountProvisioningException;
import com.jobportal.services.OAuthAccountService;
import com.jobportal.services.OAuthFailureReason;
import com.jobportal.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=",
        "GITHUB_CLIENT_SECRET="
})
class AccountDeactivationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private SavedJobRepository savedJobRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private JobAlertRepository jobAlertRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private OAuthAccountRepository oauthAccountRepository;
    @Autowired private UserService userService;
    @Autowired private OAuthAccountService oauthAccountService;

    @Test
    void deactivatingSeekerAndHrPreservesHistoryAndHrOwnership() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User admin = createUser("admin-" + suffix, UserRole.ADMIN);
        User seeker = createUser("seeker-" + suffix, UserRole.JOB_SEEKER);
        String oauthSubject = "deactivated-" + suffix;
        User hr = createOAuthUser("hr-" + suffix + "@example.test", oauthSubject,
                "HR " + suffix, UserRole.HR);
        Job job = createJob(hr, "Preserved HR-owned job " + suffix);

        Application application = new Application();
        application.setUserId(seeker.getId());
        application.setJobId(job.getId());
        application.setApplicantName(seeker.getName());
        application.setApplicantEmail(seeker.getEmail());
        application.setStatus("APPLIED");
        application.setAppliedDate(LocalDate.now());
        applicationRepository.saveAndFlush(application);
        Long applicationId = application.getId();
        Long applicationUserId = application.getUserId();
        Long applicationJobId = application.getJobId();
        String applicationStatus = application.getStatus();
        LocalDate applicationDate = application.getAppliedDate();

        SavedJob savedJob = new SavedJob();
        savedJob.setUserId(seeker.getId());
        savedJob.setJobId(job.getId());
        savedJob.setSavedAt(LocalDateTime.now());
        savedJobRepository.saveAndFlush(savedJob);

        Profile profile = new Profile();
        profile.setUserId(seeker.getId());
        profile.setHeadline("Preserved profile");
        profileRepository.saveAndFlush(profile);

        JobAlert alert = new JobAlert();
        alert.setUserId(seeker.getId());
        alert.setKeyword("Java");
        alert.setCreatedAt(LocalDateTime.now());
        jobAlertRepository.saveAndFlush(alert);

        CompanyProfile companyProfile = new CompanyProfile();
        companyProfile.setUserId(hr.getId());
        companyProfile.setCompanyName("Preserved HR Company");
        companyProfileRepository.saveAndFlush(companyProfile);

        mockMvc.perform(post("/admin/users/{id}/status", seeker.getId()).with(csrf())
                        .session(sessionFor(admin)).param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + seeker.getId()));
        mockMvc.perform(post("/admin/users/{id}/status", hr.getId()).with(csrf())
                        .session(sessionFor(admin)).param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + hr.getId()));

        assertFalse(userRepository.findById(seeker.getId()).orElseThrow().isEnabled());
        assertFalse(userRepository.findById(hr.getId()).orElseThrow().isEnabled());
        assertNull(userService.authenticateUser(seeker.getEmail(), "test-password"));
        assertNull(userService.authenticateUser(hr.getEmail(), "test-password"));
        assertEquals(hr.getId(), jobRepository.findById(job.getId()).orElseThrow().getOwnerId());
        assertTrue(applicationRepository.existsById(application.getId()));
        assertEquals(1, savedJobRepository.countByUserId(seeker.getId()));
        assertTrue(profileRepository.existsByUserId(seeker.getId()));
        assertEquals(1, jobAlertRepository.countByUserId(seeker.getId()));
        assertTrue(companyProfileRepository.existsByUserId(hr.getId()));
        assertNotNull(oauthAccountRepository.findByProviderAndProviderUserId("google", oauthSubject).orElse(null));

        assertAdminCanSeeApplication(admin, applicationId);

        mockMvc.perform(post("/admin/users/{id}/status", seeker.getId()).with(csrf())
                        .session(sessionFor(admin)).param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + seeker.getId()));

        assertTrue(userRepository.findById(seeker.getId()).orElseThrow().isEnabled());
        Application reloadedApplication = applicationRepository.findById(applicationId).orElseThrow();
        assertEquals(applicationId, reloadedApplication.getId());
        assertEquals(applicationUserId, reloadedApplication.getUserId());
        assertEquals(applicationJobId, reloadedApplication.getJobId());
        assertEquals(applicationStatus, reloadedApplication.getStatus());
        assertEquals(applicationDate, reloadedApplication.getAppliedDate());
        assertAdminCanSeeApplication(admin, applicationId);

        OAuthAccountProvisioningException disabledOAuth = assertThrows(OAuthAccountProvisioningException.class,
                () -> oauthAccountService.authenticateOrCreate("google", oauthSubject,
                        hr.getEmail(), true, hr.getName()));
        assertEquals(OAuthFailureReason.LINKED_ACCOUNT_DISABLED, disabledOAuth.getReason());
    }

    @Test
    void permanentDeletionIsBlockedForEverySupportedDependentRecordType() {
        String suffix = UUID.randomUUID().toString();
        User applicationUser = createUser("application-dependent-" + suffix, UserRole.JOB_SEEKER);
        User savedJobUser = createUser("saved-dependent-" + suffix, UserRole.JOB_SEEKER);
        User profileUser = createUser("profile-dependent-" + suffix, UserRole.JOB_SEEKER);
        User alertUser = createUser("alert-dependent-" + suffix, UserRole.JOB_SEEKER);
        User companyUser = createUser("company-dependent-" + suffix, UserRole.HR);
        User jobOwner = createUser("owner-dependent-" + suffix, UserRole.HR);

        Application application = new Application();
        application.setUserId(applicationUser.getId());
        application.setJobId(910001L);
        application.setStatus("APPLIED");
        applicationRepository.saveAndFlush(application);

        SavedJob savedJob = new SavedJob();
        savedJob.setUserId(savedJobUser.getId());
        savedJob.setJobId(910002L);
        savedJob.setSavedAt(LocalDateTime.now());
        savedJobRepository.saveAndFlush(savedJob);

        Profile profile = new Profile();
        profile.setUserId(profileUser.getId());
        profileRepository.saveAndFlush(profile);

        JobAlert alert = new JobAlert();
        alert.setUserId(alertUser.getId());
        alert.setCreatedAt(LocalDateTime.now());
        jobAlertRepository.saveAndFlush(alert);

        CompanyProfile companyProfile = new CompanyProfile();
        companyProfile.setUserId(companyUser.getId());
        companyProfile.setCompanyName("Dependent Company");
        companyProfileRepository.saveAndFlush(companyProfile);

        Job ownedJob = createJob(jobOwner, "Owned job prevents deletion");

        for (User dependentUser : new User[] { applicationUser, savedJobUser, profileUser,
                alertUser, companyUser, jobOwner }) {
            assertFalse(userService.deleteUser(dependentUser.getId()),
                    "permanent deletion should be blocked for user " + dependentUser.getId());
            assertTrue(userRepository.existsById(dependentUser.getId()));
        }
        assertTrue(applicationRepository.existsById(application.getId()));
        assertTrue(savedJobRepository.existsById(savedJob.getId()));
        assertTrue(profileRepository.existsByUserId(profileUser.getId()));
        assertEquals(1, jobAlertRepository.countByUserId(alertUser.getId()));
        assertTrue(companyProfileRepository.existsByUserId(companyUser.getId()));
        assertTrue(jobRepository.existsByOwnerId(jobOwner.getId()));
        assertTrue(jobRepository.existsById(ownedJob.getId()));
    }

    @Test
    void deleteEndpointReturnsConflictAndKeepsOauthAndProfileData() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User admin = createUser("delete-admin-" + suffix, UserRole.ADMIN);
        String oauthSubject = "delete-blocked-" + suffix;
        User hr = createOAuthUser("delete-hr-" + suffix + "@example.test", oauthSubject,
                "Delete HR " + suffix, UserRole.HR);
        Profile profile = new Profile();
        profile.setUserId(hr.getId());
        profileRepository.saveAndFlush(profile);
        mockMvc.perform(delete("/users/{id}", hr.getId()).with(csrf()).session(sessionFor(admin)))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disable the account instead")));

        assertTrue(userRepository.existsById(hr.getId()));
        assertTrue(profileRepository.existsByUserId(hr.getId()));
        assertTrue(oauthAccountRepository.findByProviderAndProviderUserId("google", oauthSubject).isPresent());
    }

    @Test
    void onlyAdminsCanChangeAccountStatus() throws Exception {
        User seeker = createUser("status-seeker-" + UUID.randomUUID(), UserRole.JOB_SEEKER);
        User anotherSeeker = createUser("status-other-" + UUID.randomUUID(), UserRole.JOB_SEEKER);

        mockMvc.perform(post("/admin/users/{id}/status", anotherSeeker.getId()).with(csrf())
                        .session(sessionFor(seeker)).param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        assertTrue(userRepository.findById(anotherSeeker.getId()).orElseThrow().isEnabled());
    }

    private User createUser(String label, UserRole role) {
        User user = new User();
        user.setName(label);
        user.setEmail(label + "@example.test");
        user.setPassword("test-password");
        user.setRole(role);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private User createOAuthUser(String email, String subject, String name, UserRole role) {
        User user = oauthAccountService.authenticateOrCreate("google", subject, email, true, name);
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Job createJob(User owner, String title) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany("Integrity Test Company");
        job.setLocation("Remote");
        job.setDescription("Test job description");
        job.setSalary("10-12 LPA");
        job.setSkills("Java");
        job.setJobType("Full-time");
        job.setWorkMode("Remote");
        job.setOwnerId(owner.getId());
        job.setPostedDate(LocalDate.now());
        job.setStatus("ACTIVE");
        return jobRepository.saveAndFlush(job);
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        User sessionUser = new User();
        sessionUser.setId(user.getId());
        sessionUser.setName(user.getName());
        sessionUser.setEmail(user.getEmail());
        sessionUser.setRole(user.getRole());
        sessionUser.setEnabled(user.isEnabled());
        session.setAttribute("loggedInUser", sessionUser);
        return session;
    }

    private void assertAdminCanSeeApplication(User admin, Long applicationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/applications").session(sessionFor(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-applications"))
                .andExpect(model().attributeExists("applications", "userMap", "jobMap"))
                .andReturn();

        Object applications = result.getModelAndView().getModel().get("applications");
        assertTrue(applications instanceof java.util.List<?>);
        assertTrue(((java.util.List<?>) applications).stream()
                .filter(Application.class::isInstance)
                .map(Application.class::cast)
                .anyMatch(candidate -> applicationId.equals(candidate.getId())));
    }
}
