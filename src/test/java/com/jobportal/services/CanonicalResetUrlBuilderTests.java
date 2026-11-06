package com.jobportal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CanonicalResetUrlBuilderTests {
    @Test
    void constructsLinkFromConfiguredCanonicalBase() {
        CanonicalResetUrlBuilder builder = new CanonicalResetUrlBuilder("https://jobs.example.test/app/", true);
        assertEquals("https://jobs.example.test/app/reset-password?token=abc_def-123",
                builder.build("abc_def-123"));
    }

    @Test
    void acceptsLocalHttpButRequiresHttpsInProduction() {
        new CanonicalResetUrlBuilder("http://localhost:8080", false);
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalResetUrlBuilder("http://jobs.example.test", true));
    }

    @Test
    void rejectsHostHeaderStyleAndMalformedCanonicalBases() {
        for (String invalid : new String[] {"", "//jobs.example.test", "https://user@jobs.example.test",
                "https://jobs.example.test?next=x", "https://jobs.example.test/#fragment",
                "https://jobs.example.test\\evil", "https://jobs.example.test/has space"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CanonicalResetUrlBuilder(invalid, true), invalid);
        }
    }
}
