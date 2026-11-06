package com.jobportal.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

class EmailChangeVerificationServiceTests {

    @Test
    void verifiedEmailChangeReplacesAddressOnlyAfterCorrectCode() {
        UserRepository users = mock(UserRepository.class);
        RegistrationEmailSender sender = mock(RegistrationEmailSender.class);
        User existing = user(7L, "old@example.test");
        when(users.findById(7L)).thenReturn(Optional.of(existing));
        when(users.findAllByEmailIgnoreCase("new@example.test")).thenReturn(List.of());
        when(users.save(existing)).thenReturn(existing);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
        EmailChangeVerificationService service = new EmailChangeVerificationService(users, sender, clock, 5, 5);
        MockHttpSession session = new MockHttpSession();

        service.startEmailChange(7L, " New@Example.Test ", session);
        assertThat(existing.getEmail()).isEqualTo("old@example.test");
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).sendOtp(eq("new@example.test"), codeCaptor.capture(), eq(5));

        assertThatThrownBy(() -> service.verifyEmailChange(7L, "wrong", session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or expired");
        assertThat(existing.getEmail()).isEqualTo("old@example.test");

        User updated = service.verifyEmailChange(7L, codeCaptor.getValue(), session);
        assertThat(updated.getEmail()).isEqualTo("new@example.test");
        assertThat(updated.isEmailVerified()).isTrue();
        verify(users).save(existing);
    }

    @Test
    void duplicateAndExpiredChangesPreserveTheExistingEmail() {
        UserRepository users = mock(UserRepository.class);
        RegistrationEmailSender sender = mock(RegistrationEmailSender.class);
        User existing = user(7L, "old@example.test");
        User other = user(8L, "taken@example.test");
        when(users.findById(7L)).thenReturn(Optional.of(existing));
        when(users.findAllByEmailIgnoreCase("taken@example.test")).thenReturn(List.of(other));
        when(users.findAllByEmailIgnoreCase("new@example.test")).thenReturn(List.of());
        MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
        EmailChangeVerificationService service = new EmailChangeVerificationService(users, sender, clock, 5, 5);
        MockHttpSession duplicateSession = new MockHttpSession();

        assertThatThrownBy(() -> service.startEmailChange(7L, "taken@example.test", duplicateSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already associated");
        verify(sender, never()).sendOtp(eq("taken@example.test"), org.mockito.ArgumentMatchers.any(), eq(5));

        MockHttpSession expiredSession = new MockHttpSession();
        service.startEmailChange(7L, "new@example.test", expiredSession);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).sendOtp(eq("new@example.test"), codeCaptor.capture(), eq(5));
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> service.verifyEmailChange(7L, codeCaptor.getValue(), expiredSession))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        assertThat(existing.getEmail()).isEqualTo("old@example.test");
        verify(users, never()).save(existing);
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setRole(UserRole.JOB_SEEKER);
        user.setEmailVerified(true);
        return user;
    }

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
