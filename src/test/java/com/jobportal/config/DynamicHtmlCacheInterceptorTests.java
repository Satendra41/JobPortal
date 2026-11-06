package com.jobportal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class DynamicHtmlCacheInterceptorTests {
    private final DynamicHtmlCacheInterceptor interceptor = new DynamicHtmlCacheInterceptor();

    @Test
    void marksServerRenderedPageResponsesAsNonCacheable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new PageHandler(), PageHandler.class.getMethod("page"));

        interceptor.preHandle(request, response, handler);

        assertEquals("private, no-store, no-cache, max-age=0, must-revalidate",
                response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals(0, response.getDateHeader("Expires"));
    }

    @Test
    void leavesApiResponsesAndPostRequestsOutsideHtmlCachePolicy() throws Exception {
        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        HandlerMethod apiHandler = new HandlerMethod(new ApiHandler(), ApiHandler.class.getMethod("api"));
        interceptor.preHandle(apiRequest, apiResponse, apiHandler);
        assertNull(apiResponse.getHeader("Cache-Control"));

        MockHttpServletRequest postRequest = new MockHttpServletRequest("POST", "/dashboard");
        MockHttpServletResponse postResponse = new MockHttpServletResponse();
        HandlerMethod pageHandler = new HandlerMethod(new PageHandler(), PageHandler.class.getMethod("page"));
        interceptor.preHandle(postRequest, postResponse, pageHandler);
        assertNull(postResponse.getHeader("Cache-Control"));
    }

    @Controller
    static class PageHandler {
        @GetMapping("/dashboard")
        public String page() { return "dashboard"; }
    }

    @RestController
    static class ApiHandler {
        @GetMapping("/users")
        public String api() { return "users"; }
    }
}
