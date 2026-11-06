package com.jobportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.jobportal.model.Job;
import com.jobportal.repository.JobRepository;

class DemoJobSeederTests {

    @Test
    void seedingIsOptInAndDisabledByDefaultWhenThePropertyIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(SeederImport.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).doesNotHaveBean(DemoJobSeeder.class));
    }

    @Test
    void localOptInAddsTwelveJobsOnceAndKeepsThemUnownedAndActive() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        DataSource dataSource = dataSourceFor("jdbc:mysql://localhost:3306/jobportal");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        Set<String> keys = new HashSet<>();
        doAnswer(invocation -> keys.contains(key(invocation.getArgument(0), invocation.getArgument(1))))
                .when(repository).existsByTitleAndCompanyAndOwnerIdIsNull(any(), any());
        doAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            assertThat(job.getOwnerId()).isNull();
            assertThat(job.getStatus()).isEqualTo("ACTIVE");
            assertThat(job.getPostedDate()).isNotNull();
            assertThat(job.getApplicationDeadline()).isAfter(LocalDate.now());
            keys.add(key(job.getTitle(), job.getCompany()));
            return job;
        }).when(repository).save(any(Job.class));

        DemoJobSeeder seeder = new DemoJobSeeder(repository, dataSource, environment);
        seeder.run(mock(ApplicationArguments.class));
        seeder.run(mock(ApplicationArguments.class));

        assertThat(DemoJobSeeder.demoJobCount()).isEqualTo(12);
        assertThat(keys).hasSize(12);
        verify(repository, org.mockito.Mockito.times(12)).save(any(Job.class));
    }

    @Test
    void remoteDatasourceIsRejectedBeforeAnyRepositoryAccess() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        DemoJobSeeder seeder = new DemoJobSeeder(repository,
                dataSourceFor("jdbc:mysql://db.example.net:3306/jobportal"), environment);

        assertThatThrownBy(() -> seeder.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost or 127.0.0.1");
        verify(repository, never()).existsByTitleAndCompanyAndOwnerIdIsNull(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void prodProfileIsRejectedBeforeDatasourceOrRepositoryAccess() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        DataSource dataSource = mock(DataSource.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");
        DemoJobSeeder seeder = new DemoJobSeeder(repository, dataSource, environment);

        assertThatThrownBy(() -> seeder.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile");
        verify(dataSource, never()).getConnection();
        verify(repository, never()).save(any());
    }

    private DataSource dataSourceFor(String jdbcUrl) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(jdbcUrl);
        return dataSource;
    }

    private String key(String title, String company) {
        return title + "\u0000" + company;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DemoJobSeeder.class)
    static class SeederImport {
    }
}
