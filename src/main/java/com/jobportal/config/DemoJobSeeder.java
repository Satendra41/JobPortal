package com.jobportal.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.jobportal.model.Job;
import com.jobportal.repository.JobRepository;

/** Opt-in fictional listings for a verified local MySQL development database. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.demo-jobs.enabled", havingValue = "true")
public class DemoJobSeeder implements ApplicationRunner {

    private static final List<DemoJob> DEMO_JOBS = List.of(
            new DemoJob("DEMO JOB — Java Developer", "DEMO · Cedarline Systems", "Bengaluru, Karnataka",
                    "Fictional demonstration vacancy. Build and maintain Java services, write readable tests, and collaborate with a product team. This sample listing is not a live vacancy.",
                    "₹8–12 LPA (demo range)", "Java, Spring Boot, REST APIs, MySQL, JUnit", "Full-time", "Hybrid", "2–4 years", 28),
            new DemoJob("DEMO JOB — Frontend Developer", "DEMO · Lumenfield Studio", "Pune, Maharashtra",
                    "Fictional demonstration vacancy. Turn accessible interface designs into responsive web pages, improve browser performance, and work with designers and API developers. This sample listing is not a live vacancy.",
                    "₹6–10 LPA (demo range)", "HTML, CSS, JavaScript, Accessibility, Responsive UI", "Full-time", "On-site", "1–3 years", 30),
            new DemoJob("DEMO JOB — Python Developer", "DEMO · Northstar Logic Works", "Remote, India",
                    "Fictional demonstration vacancy. Develop Python applications and data integrations, contribute code reviews, and improve reliability through automated tests. This sample listing is not a live vacancy.",
                    "₹7–11 LPA (demo range)", "Python, Django, PostgreSQL, REST, Pytest", "Full-time", "Remote", "2–5 years", 32),
            new DemoJob("DEMO JOB — React Developer", "DEMO · Blue Orchard Digital", "Hyderabad, Telangana",
                    "Fictional demonstration vacancy. Create reusable React components, connect product workflows to APIs, and help maintain a consistent design system. This sample listing is not a live vacancy.",
                    "₹9–14 LPA (demo range)", "React, TypeScript, HTML, CSS, Jest", "Contract", "Hybrid", "3–5 years", 34),
            new DemoJob("DEMO JOB — Backend Developer", "DEMO · Amberwave Computing", "Chennai, Tamil Nadu",
                    "Fictional demonstration vacancy. Design backend endpoints, review database access patterns, and contribute to monitoring and service documentation. This sample listing is not a live vacancy.",
                    "₹10–16 LPA (demo range)", "Java, Spring, Microservices, MySQL, Docker", "Full-time", "On-site", "4–6 years", 36),
            new DemoJob("DEMO JOB — Full Stack Developer", "DEMO · Fieldnote Software", "Gurugram, Haryana",
                    "Fictional demonstration vacancy. Work across browser and server features, clarify requirements with a small product group, and ship maintainable changes. This sample listing is not a live vacancy.",
                    "₹8–13 LPA (demo range)", "JavaScript, React, Node.js, SQL, Git", "Full-time", "Hybrid", "2–4 years", 38),
            new DemoJob("DEMO JOB — Data Analyst", "DEMO · Mosaic River Analytics", "Mumbai, Maharashtra",
                    "Fictional demonstration vacancy. Prepare datasets, build clear recurring reports, and explain analytical findings to non-technical partners. This sample listing is not a live vacancy.",
                    "₹5–9 LPA (demo range)", "SQL, Python, Excel, Data Visualization, Statistics", "Full-time", "Remote", "1–3 years", 40),
            new DemoJob("DEMO JOB — UI/UX Designer", "DEMO · Paper Kite Product Lab", "Jaipur, Rajasthan",
                    "Fictional demonstration vacancy. Map user journeys, prototype interaction patterns, and prepare accessible handoff notes in partnership with engineering. This sample listing is not a live vacancy.",
                    "₹6–10 LPA (demo range)", "Figma, Prototyping, UX Research, Design Systems, Accessibility", "Part-time", "Hybrid", "2–4 years", 42),
            new DemoJob("DEMO JOB — Android Developer", "DEMO · Kestrel Grove Mobile", "Kochi, Kerala",
                    "Fictional demonstration vacancy. Build and test Android application features, work with service APIs, and help improve release quality and app responsiveness. This sample listing is not a live vacancy.",
                    "₹7–12 LPA (demo range)", "Kotlin, Android SDK, Jetpack, REST APIs, Espresso", "Full-time", "On-site", "2–5 years", 44),
            new DemoJob("DEMO JOB — QA Engineer", "DEMO · Sunward Quality Studio", "Noida, Uttar Pradesh",
                    "Fictional demonstration vacancy. Plan functional test coverage, document defects with clear reproduction steps, and partner with developers throughout a release cycle. This sample listing is not a live vacancy.",
                    "₹5–8 LPA (demo range)", "Manual Testing, Test Cases, API Testing, Jira, SQL", "Full-time", "Hybrid", "1–3 years", 46),
            new DemoJob("DEMO JOB — DevOps Engineer", "DEMO · Cloudberry Platform Co.", "Remote, India",
                    "Fictional demonstration vacancy. Improve deployment pipelines, maintain infrastructure definitions, and support observability and incident learning for sample services. This sample listing is not a live vacancy.",
                    "₹12–18 LPA (demo range)", "AWS, Docker, Kubernetes, CI/CD, Terraform", "Contract", "Remote", "4–7 years", 48),
            new DemoJob("DEMO JOB — Software Testing Intern", "DEMO · Little Comet Labs", "Indore, Madhya Pradesh",
                    "Fictional demonstration internship. Learn to run structured test scenarios, record results, and discuss software quality with a mentor. This sample listing is not a live internship or hiring offer.",
                    "₹15,000–₹25,000 monthly (demo stipend)", "Software Testing, Test Documentation, Browser DevTools, Communication", "Internship", "On-site", "0–1 years", 50));

    private final JobRepository jobRepository;
    private final DataSource dataSource;
    private final Environment environment;

    public DemoJobSeeder(JobRepository jobRepository, DataSource dataSource, Environment environment) {
        this.jobRepository = jobRepository;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        verifyLocalProfile();
        verifyLocalDataSource();

        LocalDate today = LocalDate.now();
        for (DemoJob demo : DEMO_JOBS) {
            if (jobRepository.existsByTitleAndCompanyAndOwnerIdIsNull(demo.title(), demo.company())) {
                continue;
            }
            Job job = new Job();
            job.setTitle(demo.title());
            job.setCompany(demo.company());
            job.setLocation(demo.location());
            job.setDescription(demo.description());
            job.setSalary(demo.salary());
            job.setSkills(demo.skills());
            job.setJobType(demo.jobType());
            job.setWorkMode(demo.workMode());
            job.setExperience(demo.experience());
            job.setApplicationDeadline(today.plusDays(demo.deadlineDays()));
            job.setPostedDate(today.minusDays((demo.deadlineDays() - 28) / 2));
            job.setStatus("ACTIVE");
            job.setOwnerId(null);
            jobRepository.save(job);
        }
    }

    private void verifyLocalProfile() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Demo job seeding is explicitly blocked when the prod profile is active.");
        }
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException("Demo job seeding requires the local profile.");
        }
    }

    private void verifyLocalDataSource() throws SQLException {
        String jdbcUrl;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        String host = mysqlHost(jdbcUrl);
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            throw new IllegalStateException("Demo job seeding requires a MySQL datasource hosted on localhost or 127.0.0.1.");
        }
    }

    private String mysqlHost(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("Demo job seeding requires a local MySQL datasource.");
        }
        try {
            String mysqlUri = "mysql:" + jdbcUrl.substring("jdbc:mysql:".length());
            String host = java.net.URI.create(mysqlUri).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Could not verify the MySQL datasource host; demo job seeding was stopped.", exception);
        }
    }

    static int demoJobCount() {
        return DEMO_JOBS.size();
    }

    private record DemoJob(String title, String company, String location, String description, String salary,
            String skills, String jobType, String workMode, String experience, int deadlineDays) {
    }
}
