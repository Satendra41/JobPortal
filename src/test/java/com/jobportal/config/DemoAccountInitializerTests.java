package com.jobportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

class DemoAccountInitializerTests {

    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InitializerImport.class)
            .withBean(UserRepository.class, () -> users)
            .withPropertyValues(
                    "app.demo-accounts.enabled=true",
                    "app.demo-accounts.admin-email=local-admin@example.test",
                    "app.demo-accounts.admin-password=admin-test-password",
                    "app.demo-accounts.hr-email=local-hr@example.test",
                    "app.demo-accounts.hr-password=hr-test-password");

    @Test
    void explicitlyEnabledLocalProfileCreatesDemoAdminAndHrAccounts() {
        when(users.findAllByEmail(any())).thenReturn(List.of());
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertThat(context).hasSingleBean(AdminTestAccountInitializer.class)
                            .hasSingleBean(HrTestAccountInitializer.class);
                    context.getBean(AdminTestAccountInitializer.class).run(mock(ApplicationArguments.class));
                    context.getBean(HrTestAccountInitializer.class).run(mock(ApplicationArguments.class));
                });

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(User::getRole)
                .containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.HR);
        assertThat(captor.getAllValues()).allSatisfy(user ->
                assertThat(new BCryptPasswordEncoder().matches(
                        user.getRole() == UserRole.ADMIN ? "admin-test-password" : "hr-test-password",
                        user.getPassword())).isTrue());
    }

    @Test
    void prodProfileBlocksDemoAccountsEvenWhenExplicitlyEnabled() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AdminTestAccountInitializer.class)
                        .doesNotHaveBean(HrTestAccountInitializer.class));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void prodTakesPrecedenceWhenLocalAndProdProfilesAreBothActive() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "prod"))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AdminTestAccountInitializer.class)
                        .doesNotHaveBean(HrTestAccountInitializer.class));
        verify(users, never()).save(any(User.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ AdminTestAccountInitializer.class, HrTestAccountInitializer.class })
    static class InitializerImport {
    }
}
