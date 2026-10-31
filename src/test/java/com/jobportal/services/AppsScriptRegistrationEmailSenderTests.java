package com.jobportal.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppsScriptRegistrationEmailSenderTests {
    private static final String ENDPOINT = "https://script.google.com/macros/s/test-deployment/exec";
    private static final String REDIRECT = "https://script.googleusercontent.com/macros/echo?key=test";
    private static final String TEST_EMAIL = "recipient@example.test";
    private static final String TEST_OTP = "123456";
    private static final String TEST_SECRET = "test-only-secret";

    @Test
    void springConstructsTheConfiguredSenderForEachProfile() {
        assertInstanceOf(AppsScriptRegistrationEmailSender.class, springSenderFor("prod"));
        assertInstanceOf(SmtpRegistrationEmailSender.class, springSenderFor("local"));
    }

    @Test
    void postsExistingOtpAndAcceptsExplicitSuccessResponse() {
        TestSender test = sender(ENDPOINT, TEST_SECRET);
        test.server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"email":"recipient@example.test","otp":"123456","secret":"test-only-secret"}
                        """))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5));
        test.server.verify();
    }

    @Test
    void followsAppsScriptRedirectWithGetWithoutReplayingPost() {
        TestSender test = sender(ENDPOINT, TEST_SECRET);
        test.server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FOUND).location(java.net.URI.create(REDIRECT)));
        test.server.expect(requestTo(REDIRECT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5));
        test.server.verify();
    }

    @Test
    void failsSafelyOnHttpFailureWithoutExposingRequestOrResponseData() {
        TestSender test = sender(ENDPOINT, TEST_SECRET);
        test.server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("recipient@example.test 123456 test-only-secret"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5));

        assertEqualsSafeFailure(error);
        test.server.verify();
    }

    @Test
    void rejectsInvalidOrNegativeSuccessResponses() {
        for (String responseBody : new String[] {"not-json", "{}", "{\"success\":false}"}) {
            TestSender test = sender(ENDPOINT, TEST_SECRET);
            test.server.expect(requestTo(ENDPOINT))
                    .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

            assertEqualsSafeFailure(assertThrows(IllegalStateException.class,
                    () -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5)));
            test.server.verify();
        }
    }

    @Test
    void rejectsRedirectsOutsideGoogleAppsScriptHost() {
        TestSender test = sender(ENDPOINT, TEST_SECRET);
        test.server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .location(java.net.URI.create("https://example.test/collect")));

        assertEqualsSafeFailure(assertThrows(IllegalStateException.class,
                () -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5)));
        test.server.verify();
    }

    @Test
    void missingConfigurationFailsBeforeMakingARequest() {
        TestSender test = sender("", "");

        assertEqualsSafeFailure(assertThrows(IllegalStateException.class,
                () -> test.sender.sendOtp(TEST_EMAIL, TEST_OTP, 5)));
        test.server.verify();
    }

    private TestSender sender(String endpoint, String secret) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestSender(new AppsScriptRegistrationEmailSender(builder.build(),
                new ObjectMapper(), endpoint, secret), server);
    }

    private RegistrationEmailSender springSenderFor(String profile) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.registerBean(RestClient.Builder.class, () -> RestClient.builder());
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(AppsScriptRegistrationEmailSender.class, SmtpRegistrationEmailSender.class);
            context.refresh();

            assertTrue(context.getBeansOfType(RegistrationEmailSender.class).size() == 1);
            return context.getBean(RegistrationEmailSender.class);
        }
    }

    private void assertEqualsSafeFailure(IllegalStateException error) {
        assertTrue("Email delivery failed.".equals(error.getMessage()));
        assertTrue(error.getCause() == null);
        assertTrue(!error.getMessage().contains(TEST_EMAIL));
        assertTrue(!error.getMessage().contains(TEST_OTP));
        assertTrue(!error.getMessage().contains(TEST_SECRET));
    }

    private record TestSender(AppsScriptRegistrationEmailSender sender, MockRestServiceServer server) { }
}
