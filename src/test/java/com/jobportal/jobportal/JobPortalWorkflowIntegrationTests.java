package com.jobportal.jobportal;

import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

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
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.JobAlertRepository;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.repository.SavedJobRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.ProfileService;
import com.jobportal.services.ApplicationService;
import com.jobportal.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads/resumes",
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=",
        "GITHUB_CLIENT_SECRET="
})
class JobPortalWorkflowIntegrationTests {

    private static final String TEST_MARKER = "mockmvc-";
    private static final BCryptPasswordEncoder TEST_PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobAlertRepository jobAlertRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @MockitoSpyBean
    private ApplicationService applicationService;

    @Autowired
    private SavedJobRepository savedJobRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @MockitoSpyBean
    private ProfileService profileService;

    @Autowired
    private UserService userService;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdJobIds = new ArrayList<>();
    private final List<Long> createdApplicationIds = new ArrayList<>();

    private String testRunId;

    @BeforeEach
    void setUp() {
        testRunId = TEST_MARKER + UUID.randomUUID();
    }

    @Test
    void csrfProtectsStateChangesAndLogoutRequiresPost() throws Exception {
        User hr = createUser("csrf-hr", UserRole.HR);
        MockHttpSession session = sessionFor(hr);
        String title = testRunId + " CSRF protected job";
        long initialJobCount = jobRepository.countByOwnerId(hr.getId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/hr/jobs")
                        .session(session)
                        .param("title", title))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/hr/jobs")
                        .with(csrf().useInvalidToken())
                        .session(session)
                        .param("title", title))
                .andExpect(status().isForbidden());
        assertEquals(initialJobCount, jobRepository.countByOwnerId(hr.getId()));

        mockMvc.perform(post("/hr/jobs")
                        .session(session)
                        .param("title", title)
                        .param("company", "CSRF Labs")
                        .param("location", "Remote")
                        .param("jobType", "Full-time")
                        .param("workMode", "Hybrid")
                        .param("salary", "USD 100000")
                        .param("experience", "3 years")
                        .param("skills", "Java")
                        .param("description", "A valid request")
                        .param("applicationDeadline", "2030-12-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/dashboard"));
        Job job = findJobOwnedBy(hr, title);
        assertNotNull(job);
        createdJobIds.add(job.getId());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(contentContains("name=\"_csrf\""))
                .andExpect(contentContains("Continue with Google"))
                .andExpect(contentContains("Continue with GitHub"))
                .andExpect(contentContains("disabled"))
                .andExpect(contentContains("Social sign-in is unavailable until provider credentials are configured."));

        MockHttpSession logoutSession = sessionFor(hr);
        mockMvc.perform(get("/logout").session(logoutSession))
                .andExpect(status().isMethodNotAllowed());
        assertNotNull(logoutSession.getAttribute("loggedInUser"));
        mockMvc.perform(post("/logout").session(logoutSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertThrows(IllegalStateException.class, () -> logoutSession.getAttribute("loggedInUser"));
    }

    @Test
    void roleChangesImmediatelyRemoveOldRolePermissionsFromExistingSession() throws Exception {
        User admin = createUser("stale-role-admin", UserRole.ADMIN);
        User hr = createUser("stale-role-hr", UserRole.HR);
        MockHttpSession hrSession = sessionFor(hr);

        mockMvc.perform(post("/admin/users/{id}/role", hr.getId())
                        .session(sessionFor(admin))
                        .param("role", UserRole.JOB_SEEKER.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + hr.getId()));

        mockMvc.perform(get("/hr/dashboard").session(hrSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        assertEquals(UserRole.JOB_SEEKER,
                ((User) hrSession.getAttribute("loggedInUser")).getRole());
    }

    @Test
    void disablingAccountInvalidatesItsExistingSession() throws Exception {
        User admin = createUser("stale-disabled-admin", UserRole.ADMIN);
        User hr = createUser("stale-disabled-hr", UserRole.HR);
        MockHttpSession hrSession = sessionFor(hr);

        mockMvc.perform(post("/admin/users/{id}/status", hr.getId())
                        .session(sessionFor(admin))
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + hr.getId()));

        assertEquals(false, userRepository.findById(hr.getId()).orElseThrow().isEnabled());
        mockMvc.perform(get("/hr/dashboard").session(hrSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertThrows(IllegalStateException.class, () -> hrSession.getAttribute("loggedInUser"));
        mockMvc.perform(post("/login")
                        .param("email", hr.getEmail())
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void userRestRegistrationCannotChooseRoleOrReuseAnId() throws Exception {
        String email = testRunId + ".registration@example.com";
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999999,"name":"New user","email":"%s","password":"test-password","role":"ADMIN"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(contentContains("JOB_SEEKER"))
                .andExpect(contentDoesNotContain("password"));

        User registered = userRepository.findAllByEmail(email).stream().findFirst().orElseThrow();
        createdUserIds.add(registered.getId());
        assertEquals(UserRole.JOB_SEEKER, registered.getRole());
        assertTrue(registered.getId() != 999999L);
    }

    @Test
    void htmlRegistrationCannotReuseAnIdOrChooseRoleOrAccountStatus() throws Exception {
        User original = createUser("html-registration-id-victim", UserRole.HR);
        String originalName = original.getName();
        String originalEmail = original.getEmail();

        mockMvc.perform(post("/register")
                        .param("id", original.getId().toString())
                        .param("name", testRunId + " Separate registration")
                        .param("email", testRunId + ".separate-registration@example.com")
                        .param("password", "registration-test-password")
                        .param("role", UserRole.ADMIN.name())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User unchanged = userRepository.findById(original.getId()).orElseThrow();
        assertEquals(originalName, unchanged.getName());
        assertEquals(originalEmail, unchanged.getEmail());
        assertEquals(UserRole.HR, unchanged.getRole());
        assertTrue(unchanged.isEnabled());
        assertTrue(TEST_PASSWORD_ENCODER.matches("password", unchanged.getPassword()));

        User registered = userRepository.findAllByEmail(testRunId + ".separate-registration@example.com")
                .stream().findFirst().orElseThrow();
        createdUserIds.add(registered.getId());
        assertNotEquals(original.getId(), registered.getId());
        assertEquals(UserRole.JOB_SEEKER, registered.getRole());
        assertTrue(registered.isEnabled());
    }

    @Test
    void registrationsUseBcryptAndLegacyPasswordsUpgradeAfterSuccessfulLogin() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String registrationPassword = "registration-test-password";

        User apiRegistration = new User();
        apiRegistration.setName(testRunId + " API password user");
        apiRegistration.setEmail(testRunId + ".api-password@example.com");
        apiRegistration.setPassword(registrationPassword);
        User registeredApiUser = userService.registerUser(apiRegistration);
        createdUserIds.add(registeredApiUser.getId());

        String storedApiPassword = userRepository.findById(registeredApiUser.getId()).orElseThrow().getPassword();
        assertTrue(encoder.matches(registrationPassword, storedApiPassword));
        assertNotNull(userService.authenticateUser(registeredApiUser.getEmail(), registrationPassword));
        assertNull(userService.authenticateUser(registeredApiUser.getEmail(), "wrong-test-password"));

        User formRegistration = new User();
        formRegistration.setName(testRunId + " Form password user");
        formRegistration.setEmail(testRunId + ".form-password@example.com");
        formRegistration.setPassword(registrationPassword);
        assertEquals("success", userService.registerNewUser(formRegistration));
        User registeredFormUser = userRepository.findAllByEmail(formRegistration.getEmail()).get(0);
        createdUserIds.add(registeredFormUser.getId());
        assertTrue(encoder.matches(registrationPassword, registeredFormUser.getPassword()));

        User legacyUser = createUser("legacy-password", UserRole.JOB_SEEKER);
        legacyUser.setPassword("password");
        userRepository.save(legacyUser);
        assertNotNull(userService.authenticateUser(legacyUser.getEmail(), "password"));
        String migratedPassword = userRepository.findById(legacyUser.getId()).orElseThrow().getPassword();
        assertTrue(encoder.matches("password", migratedPassword));
    }

    @Test
    void userRestResponsesExcludePasswordsAndPutCannotChangeRoles() throws Exception {
        User seeker = createUser("api-seeker", UserRole.JOB_SEEKER);
        User otherHr = createUser("api-other-hr", UserRole.HR);
        User admin = createUser("api-admin", UserRole.ADMIN);

        mockMvc.perform(get("/users/{id}", seeker.getId()).session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(contentContains(seeker.getEmail()))
                .andExpect(contentDoesNotContain("password"));
        mockMvc.perform(get("/users/{id}", otherHr.getId()).session(sessionFor(seeker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users").session(sessionFor(admin)))
                .andExpect(status().isOk())
                .andExpect(contentDoesNotContain("password"));

        mockMvc.perform(put("/users/{id}", seeker.getId())
                        .session(sessionFor(seeker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated seeker","email":"%s","password":"new-test-password","role":"ADMIN"}
                                """.formatted(seeker.getEmail())))
                .andExpect(status().isOk())
                .andExpect(contentContains("JOB_SEEKER"))
                .andExpect(contentDoesNotContain("password"));
        assertEquals(UserRole.JOB_SEEKER, userRepository.findById(seeker.getId()).orElseThrow().getRole());

        mockMvc.perform(put("/users/{id}", otherHr.getId())
                        .session(sessionFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated HR","email":"%s","role":"ADMIN"}
                                """.formatted(otherHr.getEmail())))
                .andExpect(status().isOk())
                .andExpect(contentContains("HR"))
                .andExpect(contentDoesNotContain("password"));
        assertEquals(UserRole.HR, userRepository.findById(otherHr.getId()).orElseThrow().getRole());

        mockMvc.perform(put("/users/{id}", otherHr.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/{id}", otherHr.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userRestLoginResponseDoesNotIncludePasswordData() throws Exception {
        User user = createUser("api-login", UserRole.JOB_SEEKER);
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(contentContains("Login Successful"))
                .andExpect(contentDoesNotContain("password"))
                .andExpect(contentDoesNotContain("$2"));
    }

    @AfterEach
    void cleanUp() throws IOException {
        for (Long applicationId : createdApplicationIds) {
            applicationRepository.findById(applicationId)
                    .map(Application::getApplicationResumeFilename)
                    .ifPresent(filename -> {
                        try {
                            profileService.deleteResume(filename);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Unable to clean up test application resume.", ex);
                        }
                    });
            applicationRepository.deleteById(applicationId);
        }
        for (Long userId : createdUserIds) {
            savedJobRepository.findByUserIdOrderBySavedAtDesc(userId)
                    .forEach(savedJob -> savedJobRepository.deleteById(savedJob.getId()));
            jobAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                    .forEach(jobAlertRepository::delete);
        }
        for (Long jobId : createdJobIds) {
            jobRepository.deleteById(jobId);
        }
        for (Long userId : createdUserIds) {
            Profile profile = profileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                if (profile.getResumePath() != null) {
                    profileService.deleteResume(profile.getResumePath());
                }
                profileRepository.delete(profile);
            }
            companyProfileRepository.findByUserId(userId).ifPresent(companyProfileRepository::delete);
            userRepository.deleteById(userId);
        }
    }

    @Test
    void hrCanCreateReadUpdateAndDeleteCompanyProfile() throws Exception {
        User hr = createUser("company-profile-hr", UserRole.HR);

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hr))
                        .param("companyName", "Madhav Technologies")
                        .param("industry", "Information Technology")
                        .param("location", "Bangalore")
                        .param("companySize", "11-50 employees")
                        .param("foundedYear", "2012")
                        .param("contactEmail", "hr@example.com")
                        .param("website", "https://www.example.com")
                        .param("address", "Green Valley")
                        .param("description", "Software and technology solutions."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"))
                .andExpect(flash().attribute("message", "Company profile saved successfully."));

        CompanyProfile profile = companyProfileRepository.findByUserId(hr.getId()).orElseThrow();
        Long profileId = profile.getId();
        assertEquals(hr.getId(), profile.getUserId());
        assertEquals("Madhav Technologies", profile.getCompanyName());
        assertEquals("Information Technology", profile.getIndustry());
        assertEquals("Bangalore", profile.getLocation());

        mockMvc.perform(get("/hr/company-profile").session(sessionFor(hr)))
                .andExpect(status().isOk())
                .andExpect(view().name("company-profile"))
                .andExpect(contentContains("Madhav Technologies"))
                .andExpect(contentContains("Information Technology"));

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hr))
                        .param("companyName", "Madhav Technologies Pvt Ltd")
                        .param("industry", "Software")
                        .param("location", "Delhi")
                        .param("companySize", "51-200 employees")
                        .param("foundedYear", "2013")
                        .param("contactEmail", "updated@example.com")
                        .param("website", "https://www.updated.example.com")
                        .param("address", "Updated Address")
                        .param("description", "Updated company description."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"))
                .andExpect(flash().attribute("message", "Company profile saved successfully."));

        CompanyProfile updatedProfile = companyProfileRepository.findByUserId(hr.getId()).orElseThrow();
        assertEquals(profileId, updatedProfile.getId());
        assertEquals("Madhav Technologies Pvt Ltd", updatedProfile.getCompanyName());
        assertEquals("Software", updatedProfile.getIndustry());
        assertEquals("Delhi", updatedProfile.getLocation());
        assertEquals(1, companyProfileRepository.findAll().stream()
                .filter(companyProfile -> hr.getId().equals(companyProfile.getUserId()))
                .count());

        mockMvc.perform(post("/hr/company-profile/delete").session(sessionFor(hr)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"))
                .andExpect(flash().attribute("message", "Company profile deleted successfully."));

        assertTrue(companyProfileRepository.findByUserId(hr.getId()).isEmpty());
    }

    @Test
    void companyProfileIsRestrictedToHrUsers() throws Exception {
        User hr = createUser("company-profile-access-hr", UserRole.HR);
        User seeker = createUser("company-profile-access-seeker", UserRole.JOB_SEEKER);
        User admin = createUser("company-profile-access-admin", UserRole.ADMIN);

        mockMvc.perform(get("/hr/company-profile").session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(seeker))
                        .param("companyName", "Should Not Save"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(post("/hr/company-profile/delete").session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(get("/hr/company-profile").session(sessionFor(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(admin))
                        .param("companyName", "Should Not Save"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
        mockMvc.perform(post("/hr/company-profile/delete").session(sessionFor(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        mockMvc.perform(get("/hr/company-profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/hr/company-profile")
                        .param("companyName", "Should Not Save"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/hr/company-profile/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertTrue(companyProfileRepository.findByUserId(hr.getId()).isEmpty());
        assertTrue(companyProfileRepository.findByUserId(seeker.getId()).isEmpty());
        assertTrue(companyProfileRepository.findByUserId(admin.getId()).isEmpty());
    }

    @Test
    void eachHrOwnsOnlyTheirCompanyProfile() throws Exception {
        User hrA = createUser("company-profile-owner-a", UserRole.HR);
        User hrB = createUser("company-profile-owner-b", UserRole.HR);

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hrA))
                        .param("companyName", "Company A"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"));

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hrB))
                        .param("companyName", "Company B"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"));

        CompanyProfile profileA = companyProfileRepository.findByUserId(hrA.getId()).orElseThrow();
        CompanyProfile profileB = companyProfileRepository.findByUserId(hrB.getId()).orElseThrow();

        assertEquals("Company A", profileA.getCompanyName());
        assertEquals("Company B", profileB.getCompanyName());
        assertTrue(!profileA.getId().equals(profileB.getId()));

        mockMvc.perform(get("/hr/company-profile").session(sessionFor(hrA)))
                .andExpect(status().isOk())
                .andExpect(contentContains("Company A"))
                .andExpect(contentDoesNotContain("Company B"));

        mockMvc.perform(get("/hr/company-profile").session(sessionFor(hrB)))
                .andExpect(status().isOk())
                .andExpect(contentContains("Company B"))
                .andExpect(contentDoesNotContain("Company A"));
    }

    @Test
    void creatingCompanyProfileCannotReuseAnotherHrProfileIdOrOwnerId() throws Exception {
        User victimHr = createUser("company-profile-id-mass-assignment-victim", UserRole.HR);
        User attackerHr = createUser("company-profile-id-mass-assignment-attacker", UserRole.HR);

        CompanyProfile original = new CompanyProfile();
        original.setUserId(victimHr.getId());
        original.setCompanyName("Original Company");
        original.setDescription("Original description");
        original.setLocation("Original location");
        original.setIndustry("Original industry");
        original.setWebsite("https://original.example.com");
        original.setCompanySize("11-50 employees");
        original.setFoundedYear(2010);
        original.setContactEmail("original@example.com");
        original.setAddress("Original address");
        original.setLogoPath("original-logo.png");
        original = companyProfileRepository.save(original);

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(attackerHr))
                        .param("id", original.getId().toString())
                        .param("userId", victimHr.getId().toString())
                        .param("companyName", "Forged Company")
                        .param("description", "Forged description")
                        .param("location", "Forged location")
                        .param("industry", "Forged industry")
                        .param("website", "https://forged.example.com")
                        .param("companySize", "501-1000 employees")
                        .param("foundedYear", "2020")
                        .param("contactEmail", "forged@example.com")
                        .param("address", "Forged address")
                        .param("logoPath", "forged-logo.png"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"))
                .andExpect(flash().attribute("message", "Company profile saved successfully."));

        CompanyProfile unchanged = companyProfileRepository.findById(original.getId()).orElseThrow();
        assertEquals(victimHr.getId(), unchanged.getUserId());
        assertEquals("Original Company", unchanged.getCompanyName());
        assertEquals("Original description", unchanged.getDescription());
        assertEquals("Original location", unchanged.getLocation());
        assertEquals("Original industry", unchanged.getIndustry());
        assertEquals("https://original.example.com", unchanged.getWebsite());
        assertEquals("11-50 employees", unchanged.getCompanySize());
        assertEquals(2010, unchanged.getFoundedYear());
        assertEquals("original@example.com", unchanged.getContactEmail());
        assertEquals("Original address", unchanged.getAddress());
        assertEquals("original-logo.png", unchanged.getLogoPath());

        CompanyProfile created = companyProfileRepository.findByUserId(attackerHr.getId()).orElseThrow();
        assertNotEquals(original.getId(), created.getId());
        assertEquals(attackerHr.getId(), created.getUserId());
        assertEquals("Forged Company", created.getCompanyName());
    }

    @Test
    void blankCompanyNameIsRejected() throws Exception {
        User hr = createUser("company-profile-validation-hr", UserRole.HR);

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hr))
                        .param("companyName", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("company-profile"))
                .andExpect(model().attributeHasFieldErrors("companyProfile", "companyName"));

        assertTrue(companyProfileRepository.findByUserId(hr.getId()).isEmpty());
    }

    @Test
    void invalidCompanyWebsiteAndOversizedDescriptionAreRejected() throws Exception {
        User hr = createUser("invalid-company-fields-hr", UserRole.HR);

        mockMvc.perform(post("/hr/company-profile")
                        .session(sessionFor(hr))
                        .param("companyName", "A".repeat(256))
                        .param("website", "javascript:alert(1)")
                        .param("description", "x".repeat(10001)))
                .andExpect(status().isOk())
                .andExpect(view().name("company-profile"))
                .andExpect(model().attributeHasFieldErrors("companyProfile", "companyName", "website", "description"));

        assertTrue(companyProfileRepository.findByUserId(hr.getId()).isEmpty());
    }

    @Test
    void deleteMissingCompanyProfileShowsError() throws Exception {
        User hr = createUser("company-profile-delete-missing-hr", UserRole.HR);

        mockMvc.perform(post("/hr/company-profile/delete").session(sessionFor(hr)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/company-profile"))
                .andExpect(flash().attribute("error", "Company profile not found."));
    }

    @Test
    void authenticatedHrCanCreateJobAndPublicJobsShowIt() throws Exception {
        User hr = createUser("hr", UserRole.HR);

        mockMvc.perform(post("/hr/jobs")
                        .session(sessionFor(hr))
                        .param("title", testRunId + " Java Engineer")
                        .param("company", "MockMvc Labs")
                        .param("location", "Remote")
                        .param("jobType", "Full-time")
                        .param("workMode", "Remote")
                        .param("salary", "USD 120000")
                        .param("experience", "3-5 years")
                        .param("skills", "Java, Spring Boot")
                        .param("description", "Build reliable services.")
                        .param("applicationDeadline", "2030-12-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/dashboard"));

        Job job = findJobOwnedBy(hr, testRunId + " Java Engineer");
        assertNotNull(job);
        createdJobIds.add(job.getId());
        assertEquals("MockMvc Labs", job.getCompany());
        assertEquals("Remote", job.getLocation());
        assertEquals("Build reliable services.", job.getDescription());
        assertEquals("3-5 years", job.getExperience());
        assertEquals(hr.getId(), job.getOwnerId());
        assertEquals("ACTIVE", job.getStatus());

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs"))
                .andExpect(contentContains(job.getTitle()))
                .andExpect(contentContains("MockMvc Labs"));
    }

    @Test
    void oversizedJobDescriptionIsRejectedWithFieldError() throws Exception {
        User hr = createUser("oversized-job-description-hr", UserRole.HR);
        long initialCount = jobRepository.countByOwnerId(hr.getId());

        mockMvc.perform(post("/hr/jobs")
                        .session(sessionFor(hr))
                        .param("title", testRunId + " Engineer")
                        .param("company", "Valid Company")
                        .param("location", "Remote")
                        .param("jobType", "Full-time")
                        .param("workMode", "Remote")
                        .param("description", "x".repeat(10001)))
                .andExpect(status().isOk())
                .andExpect(view().name("hr-job-form"))
                .andExpect(model().attributeHasFieldErrors("job", "description"));

        assertEquals(initialCount, jobRepository.countByOwnerId(hr.getId()));
    }

    @Test
    void oversizedJobUpdateFieldIsRejectedWithoutChangingStoredJob() throws Exception {
        User hr = createUser("oversized-job-update-hr", UserRole.HR);
        Job original = createJob(hr, "Job update validation target");

        mockMvc.perform(post("/hr/jobs/{id}/edit", original.getId())
                        .session(sessionFor(hr))
                        .param("title", original.getTitle())
                        .param("company", "x".repeat(201))
                        .param("location", original.getLocation())
                        .param("jobType", original.getJobType())
                        .param("workMode", original.getWorkMode())
                        .param("description", original.getDescription()))
                .andExpect(status().isOk())
                .andExpect(view().name("hr-job-form"))
                .andExpect(model().attributeHasFieldErrors("job", "company"));

        Job unchanged = jobRepository.findById(original.getId()).orElseThrow();
        assertEquals("Test Company", unchanged.getCompany());
    }

    @Test
    void oversizedProfileFieldIsRejectedWithoutSaving() throws Exception {
        User seeker = createUser("oversized-profile-field", UserRole.JOB_SEEKER);

        mockMvc.perform(multipart("/profile")
                        .session(sessionFor(seeker))
                        .param("name", seeker.getName())
                        .param("headline", "x".repeat(161)))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeHasFieldErrors("profile", "headline"));

        assertTrue(profileRepository.findByUserId(seeker.getId()).isEmpty());
    }

    @Test
    void publicJobsWithoutKeywordReturnActiveJobs() throws Exception {
        User hr = createUser("browse-jobs-hr", UserRole.HR);
        Job job = createJob(hr, "Browse all jobs");

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs"))
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobSearchReturnsJavaMatches() throws Exception {
        User hr = createUser("java-search-hr", UserRole.HR);
        Job job = createJob(hr, "Java search role");

        mockMvc.perform(get("/jobs").param("keyword", "Java"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs"))
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobSearchIsCaseInsensitive() throws Exception {
        User hr = createUser("lowercase-search-hr", UserRole.HR);
        Job job = createJob(hr, "Java lowercase search role");

        mockMvc.perform(get("/jobs").param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobSearchMatchesLocation() throws Exception {
        User hr = createUser("location-search-hr", UserRole.HR);
        Job job = createJob(hr, "Bangalore location search role");
        job.setLocation("Bangalore");
        jobRepository.save(job);

        mockMvc.perform(get("/jobs").param("keyword", "Bangalore"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobLocationFilterMatchesBangalore() throws Exception {
        Job job = createBangaloreLocationSearchJob("location-exact-hr", "Bangalore location filter role");

        mockMvc.perform(get("/jobs").param("location", "Bangalore"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobLocationFilterIsCaseInsensitiveForLowercase() throws Exception {
        Job job = createBangaloreLocationSearchJob("location-lowercase-hr", "lowercase location filter role");

        mockMvc.perform(get("/jobs").param("location", "bangalore"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobLocationFilterIsCaseInsensitiveForUppercase() throws Exception {
        Job job = createBangaloreLocationSearchJob("location-uppercase-hr", "uppercase location filter role");

        mockMvc.perform(get("/jobs").param("location", "BANGALORE"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobLocationFilterSupportsPartialMatches() throws Exception {
        Job job = createBangaloreLocationSearchJob("location-partial-hr", "partial location filter role");

        mockMvc.perform(get("/jobs").param("location", "Bang"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobLocationFilterShowsNoResultsForUnknownLocation() throws Exception {
        mockMvc.perform(get("/jobs").param("location", "doesnotexist123"))
                .andExpect(status().isOk())
                .andExpect(contentContains("No jobs found matching your search."));
    }

    @Test
    void publicJobSearchShowsNoResultsMessage() throws Exception {
        mockMvc.perform(get("/jobs").param("keyword", "doesnotexist123"))
                .andExpect(status().isOk())
                .andExpect(contentContains("No jobs found matching your search."));
    }

    @Test
    void publicJobFiltersCombineKeywordAndLocation() throws Exception {
        User hr = createUser("combined-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Combined filter result", "Filter Co", "Bangalore",
                "3 years", "Full-time", "USD 100000", "Java, Spring Boot", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Combined filter excluded", "Filter Co", "Remote",
                "3 years", "Full-time", "USD 100000", "Java, Spring Boot", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("keyword", "java").param("location", "Bangalore"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobsFilterByWorkModeAndPreserveOtherSelectedFilters() throws Exception {
        User hr = createUser("work-mode-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Java work mode match", "Northwind Labs", "Bangalore",
                "3-5 years", "Full-time", "USD 100000", "Java, Spring Boot", LocalDate.of(2026, 1, 1));
        matchingJob.setWorkMode("Hybrid");
        jobRepository.save(matchingJob);
        Job excludedJob = createJobWithDetails(hr, "Java work mode excluded", "Northwind Labs", "Bangalore",
                "3-5 years", "Full-time", "USD 100000", "Java, Spring Boot", LocalDate.of(2026, 1, 1));
        excludedJob.setWorkMode("Remote");
        jobRepository.save(excludedJob);

        mockMvc.perform(get("/jobs")
                        .param("keyword", "Java")
                        .param("location", "Bangalore")
                        .param("company", "Northwind")
                        .param("experience", "3-5")
                        .param("workMode", "hybrid")
                        .param("jobType", "Full-time")
                        .param("minSalary", "90000")
                        .param("maxSalary", "110000")
                        .param("sort", "title"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("workMode", "Hybrid"))
                .andExpect(model().attribute("hasFilters", true))
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()))
                .andExpect(contentContains("value=\"Hybrid\" selected=\"selected\""))
                .andExpect(contentContains("href=\"/jobs\""))
                .andExpect(contentContains("Clear Filters"));
    }

    @Test
    void publicJobCardsDisplayWorkMode() throws Exception {
        User hr = createUser("work-mode-display-hr", UserRole.HR);
        Job job = createJob(hr, "Work mode display role");
        job.setWorkMode("On-site");
        jobRepository.save(job);

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()))
                .andExpect(contentContains("On-site"));
    }

    @Test
    void publicJobFiltersByCompany() throws Exception {
        User hr = createUser("company-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Company filter result", "Northwind Labs", "Remote",
                "2 years", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Company filter excluded", "Other Labs", "Remote",
                "2 years", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("company", "northwind"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobFiltersByExperience() throws Exception {
        User hr = createUser("experience-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Experience filter result", "Filter Co", "Remote",
                "5 years", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Experience filter excluded", "Filter Co", "Remote",
                "1 year", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("experience", "5 years"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobExperienceFilterMatchesExactValue() throws Exception {
        Job job = createExperienceSearchJob("experience-exact-hr", "exact experience filter role");

        mockMvc.perform(get("/jobs").param("experience", "2-4 years"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobExperienceFilterIsCaseInsensitive() throws Exception {
        Job job = createExperienceSearchJob("experience-case-hr", "case-insensitive experience filter role");

        mockMvc.perform(get("/jobs").param("experience", "2-4 YEARS"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobExperienceFilterSupportsPartialMatches() throws Exception {
        Job job = createExperienceSearchJob("experience-partial-hr", "partial experience filter role");

        mockMvc.perform(get("/jobs").param("experience", "2-4"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobExperienceFilterShowsNoResultsForUnknownValue() throws Exception {
        mockMvc.perform(get("/jobs").param("experience", "doesnotexist123"))
                .andExpect(status().isOk())
                .andExpect(contentContains("No jobs found matching your search."));
    }

    @Test
    void publicJobExperienceFilterCombinesWithKeyword() throws Exception {
        Job job = createExperienceSearchJob("experience-keyword-hr", "Java experience filter role");

        mockMvc.perform(get("/jobs").param("keyword", "java").param("experience", "2-4 years"))
                .andExpect(status().isOk())
                .andExpect(contentContains(job.getTitle()));
    }

    @Test
    void publicJobFiltersByJobType() throws Exception {
        User hr = createUser("job-type-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Job type filter result", "Filter Co", "Remote",
                "2 years", "Contract", "USD 100000", "Java", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Job type filter excluded", "Filter Co", "Remote",
                "2 years", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("jobType", "contract"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobFiltersBySalaryRange() throws Exception {
        User hr = createUser("salary-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Salary filter result", "Filter Co", "Remote",
                "2 years", "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Salary filter excluded", "Filter Co", "Remote",
                "2 years", "Full-time", "USD 150000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("minSalary", "90000").param("maxSalary", "110000"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobSortsByNewest() throws Exception {
        User hr = createUser("newest-sort-hr", UserRole.HR);
        Job olderJob = createJobWithDetails(hr, "Newest sort older", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2024, 1, 1));
        Job newerJob = createJobWithDetails(hr, "Newest sort newer", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("sort", "newest"))
                .andExpect(status().isOk())
                .andExpect(contentAppearsBefore(newerJob.getTitle(), olderJob.getTitle()));
    }

    @Test
    void publicJobSortsByOldest() throws Exception {
        User hr = createUser("oldest-sort-hr", UserRole.HR);
        Job olderJob = createJobWithDetails(hr, "Oldest sort older", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2024, 1, 1));
        Job newerJob = createJobWithDetails(hr, "Oldest sort newer", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(contentAppearsBefore(olderJob.getTitle(), newerJob.getTitle()));
    }

    @Test
    void publicJobSortsByTitle() throws Exception {
        User hr = createUser("title-sort-hr", UserRole.HR);
        Job alphaJob = createJobWithDetails(hr, "Alpha title sort", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));
        Job zuluJob = createJobWithDetails(hr, "Zulu title sort", "Sort Co", "Remote", "2 years",
                "Full-time", "USD 100000", "Java", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs").param("sort", "title"))
                .andExpect(status().isOk())
                .andExpect(contentAppearsBefore(alphaJob.getTitle(), zuluJob.getTitle()));
    }

    @Test
    void publicJobAppliesMultipleFiltersTogether() throws Exception {
        User hr = createUser("multi-filter-hr", UserRole.HR);
        Job matchingJob = createJobWithDetails(hr, "Multiple filters result", "Multi Filter Co", "Bangalore",
                "7 years", "Contract", "USD 125000", "Kotlin, Spring", LocalDate.of(2026, 1, 1));
        Job excludedJob = createJobWithDetails(hr, "Multiple filters excluded", "Multi Filter Co", "Bangalore",
                "7 years", "Contract", "USD 125000", "Java, Spring", LocalDate.of(2026, 1, 1));

        mockMvc.perform(get("/jobs")
                        .param("keyword", "kotlin")
                        .param("location", "Bangalore")
                        .param("company", "Multi Filter Co")
                        .param("experience", "7 years")
                        .param("jobType", "Contract")
                        .param("minSalary", "120000")
                        .param("maxSalary", "130000"))
                .andExpect(status().isOk())
                .andExpect(contentContains(matchingJob.getTitle()))
                .andExpect(contentDoesNotContain(excludedJob.getTitle()));
    }

    @Test
    void publicJobsWithEmptyFiltersReturnActiveJobsOnly() throws Exception {
        User hr = createUser("empty-filter-hr", UserRole.HR);
        Job activeJob = createJob(hr, "Empty filter active result");
        Job closedJob = createJob(hr, "Empty filter closed result");
        closedJob.setStatus("CLOSED");
        jobRepository.save(closedJob);

        mockMvc.perform(get("/jobs")
                        .param("keyword", "")
                        .param("location", "")
                        .param("company", "")
                        .param("experience", "")
                        .param("jobType", "")
                        .param("minSalary", "")
                        .param("maxSalary", ""))
                .andExpect(status().isOk())
                .andExpect(contentContains(activeJob.getTitle()))
                .andExpect(contentDoesNotContain(closedJob.getTitle()));
    }

    @Test
    void publicJobFiltersShowNoResultsMessageWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/jobs").param("company", "doesnotexist123"))
                .andExpect(status().isOk())
                .andExpect(contentContains("No jobs found matching your search."));
    }

    @Test
    void seekerCanSaveJobAndSeeSavedStateAndPage() throws Exception {
        User hr = createUser("saved-job-owner", UserRole.HR);
        User seeker = createUser("saved-job-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Saved job workflow role");

        mockMvc.perform(get("/jobs/{id}", job.getId()).session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(contentContains("Save Job"));
        mockMvc.perform(post("/jobs/{id}/save", job.getId()).session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs/" + job.getId()));

        SavedJob savedJob = savedJobRepository.findByUserIdAndJobId(seeker.getId(), job.getId()).orElseThrow();
        assertNotNull(savedJob.getSavedAt());
        mockMvc.perform(get("/saved-jobs").session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(view().name("saved-jobs"))
                .andExpect(contentContains(job.getTitle()))
                .andExpect(contentContains("Unsave"));
        mockMvc.perform(get("/jobs/{id}", job.getId()).session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(contentContains("Saved — Unsave"));
    }

    @Test
    void seekerCanUnsaveJob() throws Exception {
        User hr = createUser("unsave-owner", UserRole.HR);
        User seeker = createUser("unsave-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Unsave job role");
        saveJob(seeker, job);

        mockMvc.perform(post("/jobs/{id}/unsave", job.getId()).session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/saved-jobs"));

        assertTrue(savedJobRepository.findByUserIdAndJobId(seeker.getId(), job.getId()).isEmpty());
    }

    @Test
    void duplicateSavedJobsArePrevented() throws Exception {
        User hr = createUser("duplicate-save-owner", UserRole.HR);
        User seeker = createUser("duplicate-save-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Duplicate saved job role");

        saveJob(seeker, job);
        saveJob(seeker, job);

        assertEquals(1, savedJobRepository.findByUserIdOrderBySavedAtDesc(seeker.getId()).stream()
                .filter(savedJob -> job.getId().equals(savedJob.getJobId())).count());
    }

    @Test
    void creatingJobAlertCannotReuseAnotherUsersAlertIdOrOwnerId() throws Exception {
        User victim = createUser("job-alert-id-mass-assignment-victim", UserRole.JOB_SEEKER);
        User attacker = createUser("job-alert-id-mass-assignment-attacker", UserRole.JOB_SEEKER);
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 15, 12, 0);

        JobAlert original = new JobAlert();
        original.setUserId(victim.getId());
        original.setKeyword("Original keyword");
        original.setLocation("Original location");
        original.setJobType("Full-time");
        original.setExperience("3 years");
        original.setMinSalary("80000");
        original.setEnabled(false);
        original.setCreatedAt(originalCreatedAt);
        original = jobAlertRepository.save(original);

        mockMvc.perform(post("/job-alerts")
                        .session(sessionFor(attacker))
                        .param("id", original.getId().toString())
                        .param("userId", victim.getId().toString())
                        .param("keyword", "Forged keyword")
                        .param("location", "Forged location")
                        .param("jobType", "Contract")
                        .param("experience", "10 years")
                        .param("minSalary", "150000")
                        .param("enabled", "false")
                        .param("createdAt", "2000-01-01T00:00:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/job-alerts"));

        JobAlert unchanged = jobAlertRepository.findById(original.getId()).orElseThrow();
        assertEquals(victim.getId(), unchanged.getUserId());
        assertEquals("Original keyword", unchanged.getKeyword());
        assertEquals("Original location", unchanged.getLocation());
        assertEquals("Full-time", unchanged.getJobType());
        assertEquals("3 years", unchanged.getExperience());
        assertEquals("80000", unchanged.getMinSalary());
        assertEquals(false, unchanged.isEnabled());
        assertEquals(originalCreatedAt, unchanged.getCreatedAt());

        JobAlert created = jobAlertRepository.findByUserIdOrderByCreatedAtDesc(attacker.getId()).stream()
                .findFirst().orElseThrow();
        assertNotEquals(original.getId(), created.getId());
        assertEquals(attacker.getId(), created.getUserId());
        assertEquals("Forged keyword", created.getKeyword());
        assertEquals("Forged location", created.getLocation());
        assertEquals("Contract", created.getJobType());
        assertEquals("10 years", created.getExperience());
        assertEquals("150000", created.getMinSalary());
        assertEquals(true, created.isEnabled());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void differentSeekersCanSaveTheSameJob() throws Exception {
        User hr = createUser("shared-save-owner", UserRole.HR);
        User seekerA = createUser("shared-save-seeker-a", UserRole.JOB_SEEKER);
        User seekerB = createUser("shared-save-seeker-b", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Shared saved job role");

        saveJob(seekerA, job);
        saveJob(seekerB, job);

        assertTrue(savedJobRepository.existsByUserIdAndJobId(seekerA.getId(), job.getId()));
        assertTrue(savedJobRepository.existsByUserIdAndJobId(seekerB.getId(), job.getId()));
    }

    @Test
    void seekerOnlySeesAndCanUnsaveTheirOwnSavedJobs() throws Exception {
        User hr = createUser("saved-ownership-owner", UserRole.HR);
        User seekerA = createUser("saved-ownership-seeker-a", UserRole.JOB_SEEKER);
        User seekerB = createUser("saved-ownership-seeker-b", UserRole.JOB_SEEKER);
        Job jobA = createJob(hr, "Seeker A saved role");
        Job jobB = createJob(hr, "Seeker B saved role");
        saveJob(seekerA, jobA);
        saveJob(seekerB, jobB);

        mockMvc.perform(get("/saved-jobs").session(sessionFor(seekerA)))
                .andExpect(status().isOk())
                .andExpect(contentContains(jobA.getTitle()))
                .andExpect(contentDoesNotContain(jobB.getTitle()));
        mockMvc.perform(post("/jobs/{id}/unsave", jobA.getId()).session(sessionFor(seekerB)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/saved-jobs"));

        assertTrue(savedJobRepository.existsByUserIdAndJobId(seekerA.getId(), jobA.getId()));
        assertTrue(savedJobRepository.existsByUserIdAndJobId(seekerB.getId(), jobB.getId()));
    }

    @Test
    void anonymousUsersCannotUseSavedJobs() throws Exception {
        User hr = createUser("anonymous-save-owner", UserRole.HR);
        Job job = createJob(hr, "Anonymous saved job role");

        mockMvc.perform(post("/jobs/{id}/save", job.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/jobs/{id}/unsave", job.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/saved-jobs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void hrAndAdminCannotUseSavedJobs() throws Exception {
        User owner = createUser("role-save-owner", UserRole.HR);
        User hr = createUser("role-save-hr", UserRole.HR);
        User admin = createUser("role-save-admin", UserRole.ADMIN);
        Job job = createJob(owner, "Role restricted saved job role");

        mockMvc.perform(post("/jobs/{id}/save", job.getId()).session(sessionFor(hr)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(get("/saved-jobs").session(sessionFor(hr)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(post("/jobs/{id}/save", job.getId()).session(sessionFor(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
        mockMvc.perform(get("/saved-jobs").session(sessionFor(admin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        assertEquals(0, savedJobRepository.count());
    }

    @Test
    void invalidSavedJobTargetIsRejected() throws Exception {
        User seeker = createUser("invalid-saved-job-seeker", UserRole.JOB_SEEKER);

        mockMvc.perform(post("/jobs/{id}/save", 999999L).session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs"));

        assertEquals(0, savedJobRepository.countByUserId(seeker.getId()));
    }

    @Test
    void dashboardSavedJobCountReflectsDatabaseRecords() throws Exception {
        User hr = createUser("saved-count-owner", UserRole.HR);
        User seeker = createUser("saved-count-seeker", UserRole.JOB_SEEKER);
        Job firstJob = createJob(hr, "First saved count role");
        Job secondJob = createJob(hr, "Second saved count role");
        saveJob(seeker, firstJob);
        saveJob(seeker, secondJob);

        mockMvc.perform(get("/dashboard").session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("savedJobCount", 2L));
    }

    @Test
    void seekerCanApplyAndApplicationIsPersisted() throws Exception {
        User hr = createUser("hr", UserRole.HR);
        User seeker = createUser("seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Application workflow job");

        submitApplication(seeker, job);

        Application application = findApplication(seeker, job);
        assertNotNull(application);
        createdApplicationIds.add(application.getId());
        assertEquals(seeker.getId(), application.getUserId());
        assertEquals(job.getId(), application.getJobId());
        assertEquals("APPLIED", application.getStatus());
        assertNotNull(application.getApplicationResumeFilename());
        assertTrue(application.getApplicationResumeFilename().endsWith(".pdf"));
        assertEquals(null, application.getResume());
    }

    @Test
    void applicationSubmissionCannotReuseAnotherApplicationsIdOrUserId() throws Exception {
        User hr = createUser("application-id-mass-assignment-hr", UserRole.HR);
        User victim = createUser("application-id-mass-assignment-victim", UserRole.JOB_SEEKER);
        User attacker = createUser("application-id-mass-assignment-attacker", UserRole.JOB_SEEKER);
        Job victimJob = createJob(hr, "Original application job");
        Job attackerJob = createJob(hr, "New application job");

        Application original = new Application();
        original.setUserId(victim.getId());
        original.setJobId(victimJob.getId());
        original.setApplicantName(victim.getName());
        original.setApplicantEmail(victim.getEmail());
        original.setPhone("555-0101");
        original.setCoverLetter("Original cover letter.");
        original.setStatus("SHORTLISTED");
        original.setAppliedDate(LocalDate.now());
        original = applicationRepository.save(original);
        createdApplicationIds.add(original.getId());

        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("forged-application.pdf", "%PDF-1.4\nnew resume\n%%EOF"))
                        .session(sessionFor(attacker))
                        .param("id", original.getId().toString())
                        .param("userId", hr.getId().toString())
                        .param("jobId", attackerJob.getId().toString())
                        .param("phone", "555-0102")
                        .param("coverLetter", "Attacker's cover letter."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/success"));

        Application unchanged = applicationRepository.findById(original.getId()).orElseThrow();
        assertEquals(victim.getId(), unchanged.getUserId());
        assertEquals(victimJob.getId(), unchanged.getJobId());
        assertEquals(victim.getName(), unchanged.getApplicantName());
        assertEquals(victim.getEmail(), unchanged.getApplicantEmail());
        assertEquals("555-0101", unchanged.getPhone());
        assertEquals("Original cover letter.", unchanged.getCoverLetter());
        assertEquals("SHORTLISTED", unchanged.getStatus());

        Application submitted = findApplication(attacker, attackerJob);
        if (!createdApplicationIds.contains(submitted.getId())) {
            createdApplicationIds.add(submitted.getId());
        }
        assertNotEquals(original.getId(), submitted.getId());
        assertEquals(attacker.getId(), submitted.getUserId());
        assertEquals(attackerJob.getId(), submitted.getJobId());
        assertEquals("Attacker's cover letter.", submitted.getCoverLetter());
    }

    @Test
    void seekerCanApplyWithDocxAndHrReceivesPrivateApplicationResume() throws Exception {
        User hr = createUser("docx-resume-hr", UserRole.HR);
        User seeker = createUser("docx-resume-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "DOCX application job");

        mockMvc.perform(multipart("/applications/submit")
                        .file(docxFile("candidate.docx", validDocxBytes()))
                        .session(sessionFor(seeker))
                        .param("jobId", job.getId().toString())
                        .param("coverLetter", "A DOCX cover letter."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/success"));

        Application application = findApplication(seeker, job);
        createdApplicationIds.add(application.getId());
        assertTrue(application.getApplicationResumeFilename().endsWith(".docx"));
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(hr)))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentType().startsWith(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
    }

    @Test
    void applicationResumeRejectsMissingEmptyInvalidAndOversizedFiles() throws Exception {
        User hr = createUser("invalid-application-resume-hr", UserRole.HR);
        User seeker = createUser("invalid-application-resume-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Resume validation job");

        assertApplicationResumeRejected(seeker, job, null, "Please upload a resume file.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "empty.pdf", "application/pdf", new byte[0]),
                "Please upload a resume file.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "resume.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)),
                "Resume must have a .pdf or .docx extension.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "resume.pdf", "text/plain", "%PDF-1.4\\n%%EOF".getBytes(StandardCharsets.US_ASCII)),
                "Resume content type must match the selected file format.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "resume.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.US_ASCII)),
                "Resume must be a valid PDF file.");
        assertApplicationResumeRejected(seeker, job,
                docxFile("fake.docx", "not a zip".getBytes(StandardCharsets.US_ASCII)),
                "Resume must be a valid DOCX document.");
        assertApplicationResumeRejected(seeker, job,
                docxFile("incomplete.docx", zipWithSingleEntry("readme.txt", "not a Word package")),
                "Resume must be a valid DOCX document.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "resume.docx", "application/zip", validDocxBytes()),
                "Resume content type must match the selected file format.");
        assertApplicationResumeRejected(seeker, job,
                new MockMultipartFile("resumeFile", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]),
                "Resume must be 5 MB or smaller.");
        assertEquals(0, applicationRepository.countByJobId(job.getId()));
    }

    @Test
    void duplicateClosedAndNonexistentJobApplicationsAreRejected() throws Exception {
        User hr = createUser("application-eligibility-hr", UserRole.HR);
        User seeker = createUser("application-eligibility-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Eligibility application job");
        submitApplication(seeker, job);
        Application existing = findApplication(seeker, job);
        createdApplicationIds.add(existing.getId());

        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("duplicate.pdf", "%PDF-1.4\\nsecond\\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("jobId", job.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"))
                .andExpect(model().attributeExists("alreadyAppliedMessage"));
        assertEquals(1, applicationRepository.countByJobId(job.getId()));

        Job closed = createJob(hr, "Closed application job");
        closed.setStatus("CLOSED");
        jobRepository.save(closed);
        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("closed.pdf", "%PDF-1.4\\nclosed\\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("jobId", closed.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs/" + closed.getId()));

        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("missing-job.pdf", "%PDF-1.4\\nmissing\\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("jobId", "999999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs"));
        assertEquals(0, applicationRepository.countByJobId(closed.getId()));
    }

    @Test
    void applicationConstraintRejectsDuplicateUserAndJobPairs() {
        User hr = createUser("application-unique-constraint-hr", UserRole.HR);
        User seeker = createUser("application-unique-constraint-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Unique constraint application job");

        Application first = new Application();
        first.setUserId(seeker.getId());
        first.setJobId(job.getId());
        first.setStatus("APPLIED");
        first = applicationRepository.saveAndFlush(first);
        createdApplicationIds.add(first.getId());

        Application duplicate = new Application();
        duplicate.setUserId(seeker.getId());
        duplicate.setJobId(job.getId());
        duplicate.setStatus("APPLIED");
        assertThrows(DataIntegrityViolationException.class, () -> applicationRepository.saveAndFlush(duplicate));
    }

    @Test
    void concurrentDuplicateConstraintViolationShowsExistingDuplicateResponse() throws Exception {
        User hr = createUser("application-conflict-hr", UserRole.HR);
        User seeker = createUser("application-conflict-seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Concurrent duplicate application job");
        doReturn(false, true).when(applicationService).hasUserApplied(seeker.getId(), job.getId());
        doThrow(new DataIntegrityViolationException("Duplicate application"))
                .when(applicationService).saveApplication(any(Application.class));

        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("concurrent-application.pdf", "%PDF-1.4\nresume\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("jobId", job.getId().toString())
                        .param("coverLetter", "Concurrent submission."))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"))
                .andExpect(model().attributeExists("alreadyAppliedMessage"));

        assertEquals(0, applicationRepository.countByJobId(job.getId()));
    }

    @Test
    void owningHrCanViewApplicationAndSeekerCanSeeStatusWorkflow() throws Exception {
        User hr = createUser("hr", UserRole.HR);
        User seeker = createUser("seeker", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Status workflow job");
        submitApplication(seeker, job);
        Application application = findApplication(seeker, job);
        createdApplicationIds.add(application.getId());

        mockMvc.perform(get("/hr/jobs/{id}/applications", job.getId()).session(sessionFor(hr)))
                .andExpect(status().isOk())
                .andExpect(view().name("hr-applications"))
                .andExpect(contentContains(seeker.getEmail()))
                .andExpect(contentContains(job.getTitle()));

        mockMvc.perform(get("/hr/applications/{id}", application.getId()).session(sessionFor(hr)))
                .andExpect(status().isOk())
                .andExpect(view().name("hr-application-details"))
                .andExpect(contentContains(seeker.getEmail()))
                .andExpect(contentContains("APPLIED"));

        mockMvc.perform(post("/hr/applications/{id}/status", application.getId())
                        .session(sessionFor(hr))
                        .param("status", "SHORTLISTED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/applications/" + application.getId()));
        assertEquals("SHORTLISTED", applicationRepository.findById(application.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/hr/applications/{id}/status", application.getId())
                        .session(sessionFor(hr))
                        .param("status", "INTERVIEW"))
                .andExpect(status().is3xxRedirection());
        assertEquals("INTERVIEW", applicationRepository.findById(application.getId()).orElseThrow().getStatus());

        mockMvc.perform(get("/applications").session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(view().name("applications"))
                .andExpect(contentContains(job.getTitle()))
                .andExpect(contentContains("INTERVIEW"));
    }

    @Test
    void anonymousUsersCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/applications"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/hr/dashboard"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/hr/jobs/new"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/hr/jobs/999999/applications"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/profile/resume"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/hr/applications/999999/resume"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesRemainIsolatedAndAdminCanAccessAdminDashboard() throws Exception {
        User seeker = createUser("seeker", UserRole.JOB_SEEKER);
        User hr = createUser("hr", UserRole.HR);
        User admin = createUser("admin", UserRole.ADMIN);

        mockMvc.perform(get("/hr/dashboard").session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(get("/admin/dashboard").session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(get("/admin/dashboard").session(sessionFor(hr)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(get("/admin/dashboard").session(sessionFor(admin)))
                .andExpect(status().isOk()).andExpect(view().name("admin-dashboard"));
    }

    @Test
    void hrCannotManageNonOwnedJobOrApplication() throws Exception {
        User hrA = createUser("hr-a", UserRole.HR);
        User hrB = createUser("hr-b", UserRole.HR);
        User seeker = createUser("seeker", UserRole.JOB_SEEKER);
        Job jobA = createJob(hrA, "Owned by HR A");
        submitApplication(seeker, jobA);
        Application application = findApplication(seeker, jobA);
        createdApplicationIds.add(application.getId());

        mockMvc.perform(get("/hr/jobs/{id}/edit", jobA.getId()).session(sessionFor(hrB)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(post("/hr/jobs/{id}/edit", jobA.getId()).session(sessionFor(hrB))
                        .param("title", "Tampered job"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(post("/hr/jobs/{id}/close", jobA.getId()).session(sessionFor(hrB)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(get("/hr/jobs/{id}/applications", jobA.getId()).session(sessionFor(hrB)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(get("/hr/applications/{id}", application.getId()).session(sessionFor(hrB)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));
        mockMvc.perform(post("/hr/applications/{id}/status", application.getId()).session(sessionFor(hrB))
                        .param("status", "INTERVIEW"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hr/dashboard"));

        assertEquals("ACTIVE", jobRepository.findById(jobA.getId()).orElseThrow().getStatus());
        assertEquals("APPLIED", applicationRepository.findById(application.getId()).orElseThrow().getStatus());
    }

    @Test
    void seekerCannotReadAnotherSeekersApplication() throws Exception {
        User hr = createUser("hr", UserRole.HR);
        User seekerA = createUser("seeker-a", UserRole.JOB_SEEKER);
        User seekerB = createUser("seeker-b", UserRole.JOB_SEEKER);
        Job job = createJob(hr, "Private application job");
        submitApplication(seekerA, job);
        Application application = findApplication(seekerA, job);
        createdApplicationIds.add(application.getId());

        mockMvc.perform(get("/applications").session(sessionFor(seekerB)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("applications", empty()));
        mockMvc.perform(get("/hr/applications/{id}", application.getId()).session(sessionFor(seekerB)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void resumeUploadValidationAndAuthorizedAccess() throws Exception {
        User seeker = createUser("resume-seeker", UserRole.JOB_SEEKER);
        MockMultipartFile validPdf = pdfFile("resume.pdf", "%PDF-1.4\nmock resume\n%%EOF");

        mockMvc.perform(multipart("/profile")
                        .file(validPdf)
                        .session(sessionFor(seeker))
                        .param("name", seeker.getName())
                        .param("phone", "555-0100")
                        .param("location", "Remote")
                        .param("headline", "Engineer")
                        .param("skills", "Java")
                        .param("education", "Computer Science")
                        .param("experience", "3 years"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("message", "Profile saved successfully."));

        Profile profile = profileRepository.findByUserId(seeker.getId()).orElseThrow();
        assertNotNull(profile.getResumePath());
        assertTrue(profile.getResumePath().endsWith(".pdf"));
        mockMvc.perform(get("/profile/resume").session(sessionFor(seeker)))
                .andExpect(status().isOk())
                .andExpect(contentTypePdf());

        User otherSeeker = createUser("other-resume-seeker", UserRole.JOB_SEEKER);
        mockMvc.perform(get("/profile/resume").session(sessionFor(otherSeeker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/profile/resume"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

        MockMultipartFile invalidPdf = new MockMultipartFile("resumeFile", "resume.pdf", "application/pdf",
                "not a pdf".getBytes(StandardCharsets.UTF_8));
        User invalidSeeker = createUser("invalid-resume-seeker", UserRole.JOB_SEEKER);
        mockMvc.perform(multipart("/profile").file(invalidPdf).session(sessionFor(invalidSeeker))
                        .param("name", invalidSeeker.getName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Unable to save your profile or resume. Please check the form and selected file, then try again."));
        assertTrue(profileRepository.findByUserId(invalidSeeker.getId()).isEmpty());

        byte[] oversizedContent = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile oversizedPdf = new MockMultipartFile("resumeFile", "large.pdf", "application/pdf",
                oversizedContent);
        User oversizedSeeker = createUser("oversized-resume-seeker", UserRole.JOB_SEEKER);
        mockMvc.perform(multipart("/profile").file(oversizedPdf).session(sessionFor(oversizedSeeker))
                        .param("name", oversizedSeeker.getName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Unable to save your profile or resume. Please check the form and selected file, then try again."));
        assertTrue(profileRepository.findByUserId(oversizedSeeker.getId()).isEmpty());
    }

    @Test
    void profileUploadFailureShowsGenericMessageWithoutTechnicalDetails() throws Exception {
        User seeker = createUser("profile-upload-storage-error", UserRole.JOB_SEEKER);
        doThrow(new IOException("internal storage detail"))
                .when(profileService).storeResume(any(org.springframework.web.multipart.MultipartFile.class));

        mockMvc.perform(multipart("/profile")
                        .file(pdfFile("resume.pdf", "%PDF-1.4\nresume\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("name", seeker.getName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Unable to save your profile or resume. Please check the form and selected file, then try again."))
                .andExpect(contentDoesNotContain("internal storage detail"));

        assertTrue(profileRepository.findByUserId(seeker.getId()).isEmpty());
    }

    @Test
    void hrCanAccessApplicantResumeOnlyForOwnedJob() throws Exception {
        User hrA = createUser("resume-hr-a", UserRole.HR);
        User hrB = createUser("resume-hr-b", UserRole.HR);
        User seeker = createUser("applicant-with-resume", UserRole.JOB_SEEKER);
        uploadResume(seeker);
        Job jobA = createJob(hrA, "Resume ownership job");
        submitApplication(seeker, jobA);
        Application application = findApplication(seeker, jobA);
        createdApplicationIds.add(application.getId());

        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(hrA)))
                .andExpect(status().isOk())
                .andExpect(contentTypePdf());
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(hrB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()))
                .andExpect(status().isForbidden());
        User admin = createUser("resume-admin", UserRole.ADMIN);
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void olderApplicationWithoutSpecificResumeFallsBackToAuthorizedProfileResume() throws Exception {
        User hr = createUser("legacy-resume-hr", UserRole.HR);
        User seeker = createUser("legacy-resume-seeker", UserRole.JOB_SEEKER);
        uploadResume(seeker);
        Job job = createJob(hr, "Legacy resume application job");
        submitApplication(seeker, job);
        Application application = findApplication(seeker, job);
        createdApplicationIds.add(application.getId());

        application.setApplicationResumeFilename(null);
        application.setResume("legacy resume text");
        applicationRepository.save(application);
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(hr)))
                .andExpect(status().isOk())
                .andExpect(contentTypePdf());
        assertEquals("legacy resume text",
                applicationRepository.findById(application.getId()).orElseThrow().getResume());

        application.setApplicationResumeFilename("../outside-resume.pdf");
        applicationRepository.save(application);
        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId()).session(sessionFor(hr)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/hr/applications/{id}/resume", application.getId())
                        .session(sessionFor(createUser("other-legacy-resume-hr", UserRole.HR))))
                .andExpect(status().isForbidden());
    }

    private User createUser(String label, UserRole role) {
        User user = new User();
        user.setName(testRunId + " " + label);
        user.setEmail(testRunId + "." + label + "@example.com");
        user.setPassword(TEST_PASSWORD_ENCODER.encode("password"));
        user.setRole(role);
        User savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());
        return savedUser;
    }

    private Job createJob(User owner, String title) {
        Job job = new Job();
        job.setTitle(testRunId + " " + title);
        job.setCompany("Test Company");
        job.setLocation("Remote");
        job.setDescription("Test description");
        job.setSalary("USD 100000");
        job.setSkills("Java, Spring Boot");
        job.setJobType("Full-time");
        job.setWorkMode("Remote");
        job.setExperience("2-4 years");
        job.setApplicationDeadline(LocalDate.of(2030, 12, 31));
        job.setOwnerId(owner.getId());
        job.setPostedDate(LocalDate.now());
        job.setStatus("ACTIVE");
        Job savedJob = jobRepository.save(job);
        createdJobIds.add(savedJob.getId());
        return savedJob;
    }

    private Job createJobWithDetails(User owner, String title, String company, String location, String experience,
            String jobType, String salary, String skills, LocalDate postedDate) {
        Job job = createJob(owner, title);
        job.setCompany(company);
        job.setLocation(location);
        job.setExperience(experience);
        job.setJobType(jobType);
        job.setSalary(salary);
        job.setSkills(skills);
        job.setPostedDate(postedDate);
        return jobRepository.save(job);
    }

    private Job createBangaloreLocationSearchJob(String hrLabel, String title) {
        User hr = createUser(hrLabel, UserRole.HR);
        return createJobWithDetails(hr, title, "Location Filter Co", "Bangalore", "2 years", "Full-time",
                "USD 100000", "Java", LocalDate.of(2026, 1, 1));
    }

    private Job createExperienceSearchJob(String hrLabel, String title) {
        User hr = createUser(hrLabel, UserRole.HR);
        return createJobWithDetails(hr, title, "Experience Filter Co", "Remote", "2-4 years", "Full-time",
                "USD 100000", "Java", LocalDate.of(2026, 1, 1));
    }

    private void saveJob(User seeker, Job job) throws Exception {
        mockMvc.perform(post("/jobs/{id}/save", job.getId()).session(sessionFor(seeker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/jobs/" + job.getId()));
    }

    private void submitApplication(User seeker, Job job) throws Exception {
        mockMvc.perform(multipart("/applications/submit")
                        .file(pdfFile("application-resume.pdf", "%PDF-1.4\nresume\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("jobId", job.getId().toString())
                        .param("phone", "555-0100")
                        .param("coverLetter", "A realistic cover letter."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/success"));
    }

    private void assertApplicationResumeRejected(User seeker, Job job, MockMultipartFile file, String message)
            throws Exception {
        var request = multipart("/applications/submit");
        request.session(sessionFor(seeker));
        request.param("jobId", job.getId().toString());
        request.param("coverLetter", "Validation test");
        if (file != null) {
            request.file(file);
        }
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("apply"))
                .andExpect(model().attribute("error", message));
    }

    private void uploadResume(User seeker) throws Exception {
        mockMvc.perform(multipart("/profile")
                        .file(pdfFile("resume.pdf", "%PDF-1.4\nresume\n%%EOF"))
                        .session(sessionFor(seeker))
                        .param("name", seeker.getName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
    }

    private MockMultipartFile pdfFile(String filename, String content) {
        return new MockMultipartFile("resumeFile", filename, "application/pdf",
                content.getBytes(StandardCharsets.US_ASCII));
    }

    private MockMultipartFile docxFile(String filename, byte[] content) {
        return new MockMultipartFile("resumeFile", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
    }

    private byte[] validDocxBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>Resume</w:t></w:r></w:p></w:body>
                    </w:document>
                    """);
        }
        return output.toByteArray();
    }

    private byte[] zipWithSingleEntry(String name, String content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipEntry(zip, name, content);
        }
        return output.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
                session.setAttribute("loggedInUser", user);
        return session;
    }

    private MockHttpServletRequestBuilder post(String uriTemplate, Object... uriVariables) {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(uriTemplate, uriVariables);
        request.with(csrf());
        return request;
    }

    private MockHttpServletRequestBuilder put(String uriTemplate, Object... uriVariables) {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(uriTemplate, uriVariables);
        request.with(csrf());
        return request;
    }

    private MockHttpServletRequestBuilder delete(String uriTemplate, Object... uriVariables) {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete(uriTemplate, uriVariables);
        request.with(csrf());
        return request;
    }

    private MockMultipartHttpServletRequestBuilder multipart(String uriTemplate, Object... uriVariables) {
        MockMultipartHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(uriTemplate, uriVariables);
        request.with(csrf());
        return request;
    }

    private Job findJobOwnedBy(User owner, String title) {
        return jobRepository.findAll().stream()
                .filter(job -> owner.getId().equals(job.getOwnerId()) && title.equals(job.getTitle()))
                .findFirst()
                .orElseThrow();
    }

    private Application findApplication(User seeker, Job job) {
        return applicationRepository.findByUserIdOrderByAppliedDateDesc(seeker.getId()).stream()
                .filter(application -> job.getId().equals(application.getJobId()))
                .findFirst()
                .orElseThrow();
    }

    private static ResultMatcher contentContains(String expected) {
        return result -> assertTrue(result.getResponse().getContentAsString().contains(expected),
                "Expected response to contain: " + expected);
    }

    private static ResultMatcher contentDoesNotContain(String unexpected) {
        return result -> assertTrue(!result.getResponse().getContentAsString().contains(unexpected),
                "Expected response not to contain: " + unexpected);
    }

    private static ResultMatcher contentAppearsBefore(String first, String second) {
        return result -> {
            String content = result.getResponse().getContentAsString();
            int firstIndex = content.indexOf(first);
            int secondIndex = content.indexOf(second);
            assertTrue(firstIndex >= 0 && secondIndex >= 0 && firstIndex < secondIndex,
                    "Expected '" + first + "' to appear before '" + second + "'.");
        };
    }

    private static ResultMatcher contentTypePdf() {
        return result -> assertTrue(result.getResponse().getContentType().startsWith("application/pdf"),
                "Expected PDF content type but was: " + result.getResponse().getContentType());
    }
}
