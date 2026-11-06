package com.jobportal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppsScriptPasswordResetEmailSenderTests {
    private static final String ENDPOINT = "https://script.google.com/macros/s/mock/exec";
    private static final String REDIRECT = "https://script.googleusercontent.com/macros/echo?mock=1";
    private static final String EMAIL = "candidate@example.test";
    private static final String URL = "https://jobs.example.test/reset-password?token=secret-token-value";
    private static final String SECRET = "test-only-shared-secret";

    @Test
    void sendsDedicatedResetPayloadAndAcceptsExplicitSuccess() {
        TestSender test = sender();
        test.server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"messageType":"password_reset","email":"candidate@example.test",
                         "resetUrl":"https://jobs.example.test/reset-password?token=secret-token-value",
                         "secret":"test-only-shared-secret"}
                        """))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        test.sender.sendPasswordResetLink(EMAIL, URL);
        test.server.verify();
    }

    @Test
    void followsAppsScriptRedirectUsingGetWithoutReplayingPayload() {
        TestSender test = sender();
        test.server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FOUND).location(java.net.URI.create(REDIRECT)));
        test.server.expect(requestTo(REDIRECT)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        test.sender.sendPasswordResetLink(EMAIL, URL);
        test.server.verify();
    }

    @Test
    void failsWithGenericMessageAndDoesNotExposeSensitivePayload() {
        TestSender test = sender();
        test.server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .body(EMAIL + " " + URL + " " + SECRET));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> test.sender.sendPasswordResetLink(EMAIL, URL));
        assertEquals("Email delivery failed.", failure.getMessage());
        assertEquals(null, failure.getCause());
        assertTrue(!failure.toString().contains(URL));
        test.server.verify();
    }

    @Test
    void rejectsProviderSuccessFalseResponse() {
        TestSender test = sender();
        test.server.expect(requestTo(ENDPOINT)).andRespond(
                withSuccess("{\"success\":false,\"error\":\"delivery_failed\"}", MediaType.APPLICATION_JSON));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> test.sender.sendPasswordResetLink(EMAIL, URL));
        assertEquals("Email delivery failed.", failure.getMessage());
        test.server.verify();
    }

    @Test
    void requestDtoToStringIsRedacted() {
        AppsScriptPasswordResetEmailSender.PasswordResetRequest request =
                new AppsScriptPasswordResetEmailSender.PasswordResetRequest("password_reset", EMAIL, URL, SECRET);
        String display = request.toString();
        assertTrue(display.contains("redacted"));
        assertTrue(!display.contains(EMAIL));
        assertTrue(!display.contains(URL));
        assertTrue(!display.contains(SECRET));
    }

    private TestSender sender() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestSender(new AppsScriptPasswordResetEmailSender(builder.build(), new ObjectMapper(),
                ENDPOINT, SECRET), server);
    }

    private record TestSender(AppsScriptPasswordResetEmailSender sender, MockRestServiceServer server) { }
}
