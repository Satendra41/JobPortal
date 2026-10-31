package com.jobportal.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Component
@Profile("prod")
public class AppsScriptRegistrationEmailSender implements RegistrationEmailSender {
    private static final String FAILURE_MESSAGE = "Email delivery failed.";
    private static final int MAX_REDIRECTS = 3;
    private static final String SCRIPT_HOST = "script.google.com";
    private static final String REDIRECT_HOST = "script.googleusercontent.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiSecret;

    public AppsScriptRegistrationEmailSender(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.otp-email.api-url:}") String apiUrl,
            @Value("${app.otp-email.api-secret:}") String apiSecret) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiSecret = apiSecret;
    }

    AppsScriptRegistrationEmailSender(RestClient restClient, ObjectMapper objectMapper,
            String apiUrl, String apiSecret) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiSecret = apiSecret;
    }

    @Override
    public void sendOtp(String email, String otp, int expirationMinutes) {
        if (apiUrl == null || apiUrl.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw deliveryFailure();
        }

        try {
            URI endpoint = URI.create(apiUrl);
            if (!"https".equalsIgnoreCase(endpoint.getScheme())
                    || !SCRIPT_HOST.equalsIgnoreCase(endpoint.getHost())
                    || endpoint.getUserInfo() != null) {
                throw deliveryFailure();
            }

            ApiResponse response = request(HttpMethod.POST, endpoint,
                    new OtpEmailRequest(email, otp, apiSecret));
            int redirects = 0;
            while (isRedirect(response.status())) {
                if (++redirects > MAX_REDIRECTS || response.location() == null) {
                    throw deliveryFailure();
                }
                URI redirect = endpoint.resolve(response.location());
                if (!"https".equalsIgnoreCase(redirect.getScheme())
                        || !REDIRECT_HOST.equalsIgnoreCase(redirect.getHost())
                        || redirect.getUserInfo() != null) {
                    throw deliveryFailure();
                }
                // Apps Script ContentService redirects to a googleusercontent URL. Fetch it
                // with GET; never replay the OTP POST on a redirect.
                response = request(HttpMethod.GET, redirect, null);
            }

            if (!HttpStatusCode.valueOf(response.status()).is2xxSuccessful()
                    || !reportsSuccess(response.body())) {
                throw deliveryFailure();
            }
        } catch (Exception ex) {
            // Do not include provider response bodies, request data, or exception text.
            throw deliveryFailure();
        }
    }

    private ApiResponse request(HttpMethod method, URI uri, OtpEmailRequest request) {
        if (request == null) {
            return restClient.method(method).uri(uri)
                    .exchange((clientRequest, response) -> new ApiResponse(
                            response.getStatusCode().value(),
                            response.getHeaders().getFirst(HttpHeaders.LOCATION),
                            StreamUtils.copyToString(response.getBody(), java.nio.charset.StandardCharsets.UTF_8)));
        }
        return restClient.method(method).uri(uri)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((clientRequest, response) -> new ApiResponse(
                        response.getStatusCode().value(),
                        response.getHeaders().getFirst(HttpHeaders.LOCATION),
                        StreamUtils.copyToString(response.getBody(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private boolean reportsSuccess(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        JsonNode body = objectMapper.readTree(responseBody);
        return body != null && body.path("success").isBoolean() && body.path("success").booleanValue();
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303;
    }

    private IllegalStateException deliveryFailure() {
        return new IllegalStateException(FAILURE_MESSAGE);
    }

    private record OtpEmailRequest(String email, String otp, String secret) {
        @Override
        public String toString() {
            return "OtpEmailRequest[redacted]";
        }
    }

    private record ApiResponse(int status, String location, String body) { }
}
