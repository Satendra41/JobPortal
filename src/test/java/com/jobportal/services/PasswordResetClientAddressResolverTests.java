package com.jobportal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PasswordResetClientAddressResolverTests {
    @Test
    void usesOnlyContainerResolvedRemoteAddressAndIgnoresForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.17");
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        assertEquals("cb007111", new PasswordResetClientAddressResolver().resolve(request));
    }

    @Test
    void normalizesEquivalentIpv4MappedIpv6Address() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:203.0.113.17");
        assertEquals("cb007111", new PasswordResetClientAddressResolver().resolve(request));
    }
}
