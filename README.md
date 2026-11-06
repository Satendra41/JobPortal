# JAGAQ Job Portal

JAGAQ Job Portal is a server-rendered recruitment application for job seekers, HR/recruiters, and administrators. It uses Spring Boot, Thymeleaf, and MySQL. Users can browse and manage job listings, submit applications, and manage their accounts through role-specific pages.

## Main features

### Job Seeker

- Register with email verification, sign in with email and password, or use configured Google or GitHub sign-in.
- Maintain a profile and upload a PDF profile resume.
- Search and filter job listings, save jobs, and configure job alerts.
- Apply to jobs with an application-specific PDF or DOCX resume and review application status.
- Change a verified email address and request a password reset.

### HR / Recruiter

- Manage jobs they own, including creating, editing, and closing listings.
- Maintain a company profile and set job work mode.
- Review applicants for their jobs, view applicant details and resumes, and update application status.

### Admin

- View a dashboard of users, jobs, and applications.
- View users and user details; enable or disable Job Seeker and HR accounts and change their roles.
- View jobs and applications.

## Technology stack

- Java 25 (configured in `pom.xml`)
- Spring Boot 3.5.16
- Spring MVC, Spring Data JPA, Hibernate, Spring Security, and Spring Security OAuth2 Client
- Thymeleaf, HTML, CSS, JavaScript, and Bootstrap styling
- MySQL Connector/J for the application database
- Flyway Core and Flyway MySQL for optional SQL migrations
- Maven Wrapper; H2 is used by tests

## Project architecture

The application follows a conventional Spring MVC structure. Controllers handle page and form requests; services implement account, job, application, email, and resume workflows; repositories use Spring Data JPA; and model classes represent persisted records. Thymeleaf templates render HTML pages, with static CSS, JavaScript, and favicon assets under `src/main/resources/static`.

Authentication uses Spring Security and an HTTP session. Application pages apply role checks for Job Seeker, HR, and Admin actions. Local email/password registration requires email verification; Google and GitHub OAuth are optional and enabled only when each provider's client ID and secret are configured.

## Folder structure

```text
src/main/java/com/jobportal/
  config/       Security, OAuth, session, and background-worker configuration
  controller/   MVC controllers
  dto/          Data transfer objects
  model/        JPA entities and role types
  repository/   Spring Data repositories
  services/     Business logic and integrations
src/main/resources/
  db/migration/ Flyway migrations V1, V2, and V3
  static/       CSS, JavaScript, and other static assets
  templates/    Thymeleaf pages and fragments
  application*.properties
src/test/java/  Unit and integration tests
src/test/resources/ Test configuration (H2)
```

## Database overview

MySQL is the configured local/application database. JPA entities cover users and profiles, jobs and applications, saved jobs and alerts, company profiles, pending registrations, OAuth accounts, and password-reset records. With the default `spring.jpa.hibernate.ddl-auto=update`, Hibernate manages the application schema; it is not a production schema migration strategy.

Flyway is included but **opt-in** (`FLYWAY_ENABLED` defaults to `false`). The supplied migrations add password-reset credential versioning and token storage (V1), reset request rate-limit records (V2), and an encrypted email outbox (V3). These migrations have only been tested against isolated H2 databases. Their behavior against MySQL, especially an existing installation, has not been verified. Do not run these migrations against production.

## Authentication and security

- Spring Security protects form workflows and uses CSRF protection for state-changing requests.
- Session cookies are HTTP-only and use `SameSite=Lax`; the `prod` profile sets the Secure flag. Use HTTPS for any hosted environment.
- Passwords are stored using Spring Security's password encoder. Password rules are enforced by the application.
- Registration creates a pending account until its email verification code is accepted. Codes expire and attempts/resends are limited. Existing accounts with a null legacy email-verification value remain sign-in capable.
- Google and GitHub OAuth require a verified provider email. OAuth accounts are matched by provider and provider user ID; automatic linking to an existing email account is not provided.
- Configure trusted proxy address handling correctly when deployed behind a proxy. The `prod` profile trusts no forwarded-address sources by default; set `PASSWORD_RESET_TRUSTED_PROXY_REGEX` only to the exact trusted proxy range(s).

## Password reset workflow

The reset request endpoint queues work in a database-backed outbox. A scheduled worker applies account/client request limits, issues a single-use token with a 15-minute lifetime, and queues an encrypted email payload. Tokens are stored as digests; outbox payloads use AES-GCM encryption. Delivery retries are managed by the worker. Configure a stable, secret rate-limit hash key and an active outbox encryption key before enabling reset email delivery. Preserve encryption keys needed to read queued items during key rotation.

Reset links contain the raw token in a query parameter. Before production use, configure the host or reverse proxy to redact reset-token query parameters from access logs and any request tracing or analytics. The app's safe logging does not remove the token from hosting-layer access logs.

## Email verification and integrations

Email/password registration uses a six-digit verification code. The default lifetime is five minutes, with configurable attempt and resend limits. In the default/local profile, email sending uses SMTP. In the `prod` profile, registration and password-reset messages use the configured Google Apps Script HTTPS endpoint. SMTP and Apps Script integrations are configurable, but **live email delivery has not been verified**.

Google and GitHub sign-in are optional. Each provider is enabled only when both corresponding credentials are present. No live provider sign-in has been verified with real credentials.

## Prerequisites

- JDK 25
- MySQL 8.x running locally
- Windows PowerShell (commands below use the Maven Wrapper)
- Internet access for Maven dependency downloads on the first build

## Local installation and setup

From PowerShell, open the project directory and create a local database:

```powershell
Set-Location C:\Downloads\jobportal\jobportal
```

```sql
CREATE DATABASE jobportal;
```

Run that SQL in your local MySQL client. Then configure the process environment for the current PowerShell session (replace placeholders with your local values; do not commit secrets):

```powershell
$env:DB_USERNAME = "your-local-mysql-user"
$env:DB_PASSWORD = "your-local-mysql-password"
```

The local profile defaults to `jdbc:mysql://localhost:3306/jobportal`. Set `DB_URL` only if your local MySQL uses another JDBC URL. Schema tables are created/updated by Hibernate on startup with the default configuration.

## Environment variables and configuration

Configuration is read from `application.properties` and profile-specific property files. The default profile is `local`; `application-prod.properties` overrides the database URL, session cookie security, forwarded-header behavior, and password-reset settings.

| Variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials (required) |
| `DB_URL` | JDBC URL; optional locally, required by the `prod` profile |
| `PORT` | HTTP port (defaults to `8080`) |
| `FLYWAY_ENABLED` | Enables Flyway; defaults to `false` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Optional Google OAuth; both are needed |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | Optional GitHub OAuth; both are needed |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP delivery in non-`prod` profiles; port defaults to `587` |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | SMTP options; both default to `true` |
| `OTP_EMAIL_API_URL`, `OTP_EMAIL_API_SECRET` | Apps Script delivery in `prod`; URL and shared secret are required for delivery |
| `REGISTRATION_OTP_EXPIRATION_MINUTES` | Verification-code lifetime; defaults to `5` |
| `REGISTRATION_OTP_MAX_ATTEMPTS` | Verification attempts; defaults to `5` |
| `REGISTRATION_OTP_RESEND_COOLDOWN_SECONDS` | Resend cooldown; defaults to `60` |
| `REGISTRATION_OTP_MAX_RESENDS_PER_HOUR` | Hourly resend limit; defaults to `5` |
| `PASSWORD_RESET_BASE_URL` | Canonical base URL used in reset links; defaults to local URL outside `prod`, required by `prod` |
| `PASSWORD_RESET_RATE_LIMIT_HASH_KEY` | Secret key for hashing rate-limit identifiers; required by `prod` |
| `PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS` | Key ring for encrypting/decrypting outbox payloads; required by `prod` |
| `PASSWORD_RESET_OUTBOX_ACTIVE_KEY_ID` | ID of the active encryption key; required by `prod` |
| `PASSWORD_RESET_ACCOUNT_MAX_REQUESTS`, `PASSWORD_RESET_CLIENT_MAX_REQUESTS` | Reset request limits; defaults to `5` per account and `20` per client |
| `PASSWORD_RESET_OUTBOX_WORKER_ENABLED`, `PASSWORD_RESET_OUTBOX_POLL_INTERVAL_MS` | Outbox worker controls; defaults to enabled and `5000` ms |
| `PASSWORD_RESET_TRUSTED_PROXY_REGEX` | Exact trusted proxy address/range regex; defaults to trusting none |
| `SESSION_COOKIE_SECURE` | Secure-cookie flag outside `prod`; defaults to `false` |
| `DEMO_ACCOUNTS_ENABLED`, `DEMO_HR_EMAIL`, `DEMO_HR_PASSWORD`, `DEMO_ADMIN_EMAIL`, `DEMO_ADMIN_PASSWORD` | Optional demo account initialization; disabled by default |

The `prod` profile requires explicit database and password-reset URL/key configuration; it does not supply secret values. Store secrets in the host's secret environment configuration, not in source control. The `prod` profile does not establish that the application is deployed or production-ready.

## How to run the application

With MySQL running and local database variables set, start the application from PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080` in a browser. To stop the application, press `Ctrl+C` in the PowerShell window. Configure SMTP if you need to receive verification or password-reset email; without a working sender, those emails cannot be delivered.

## How to run tests

Tests use the H2 in-memory database configuration and do not require a live MySQL server:

```powershell
.\mvnw.cmd test
```

The latest available Maven Surefire reports in `target/surefire-reports` record 189 tests, with zero failures or errors. These reports are existing build artifacts; this README update did not rerun the suite.

## Database migration instructions

Flyway is disabled by default. V1, V2, and V3 are provided for isolated testing of password-reset schema changes and are not validated as a MySQL upgrade path. Keep `FLYWAY_ENABLED` unset or `false` for normal local development, where Hibernate's configured `ddl-auto=update` manages schema changes. Do not enable or run these migrations against production. A reviewed, environment-specific migration and backup/rollback plan is needed before any production schema change.

## Known limitations and production deployment requirements

- Resume files are stored on the local filesystem under `uploads/resumes` by default, while only their stored names/paths are kept in the database. This requires persistent, private storage and backups; ephemeral hosting filesystems can lose uploads.
- Database-backed password-reset outbox and rate-limit state require a durable database. V1, V2, and V3 have not been validated against MySQL or production data.
- Live SMTP, Apps Script email delivery, and Google/GitHub sign-in have not been verified.
- Reset-token query parameters must be redacted from hosting access logs before production use.
- Production operation also requires HTTPS, correct secure-cookie and trusted-proxy settings, secret management, database migration/recovery planning, monitoring, and persistent private resume storage.
- No deployment or production-readiness claim is made by this repository documentation.

## Future improvements

Before considering a hosted production deployment, validate and document a supported MySQL migration path, verify real email and OAuth integrations in controlled environments, and provide durable private resume storage with backup and retention policies.
