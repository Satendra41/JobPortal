# JobPortal

A full-stack Job Portal web application built with Java Spring Boot, MySQL, Thymeleaf, HTML, CSS, JavaScript and Bootstrap.

## 📌 Project Overview

JobPortal is a web-based recruitment platform connecting job seekers and recruiters.

Job seekers can create profiles, upload resumes, search and apply for jobs, save jobs and track application status.

HR users can create and manage jobs, manage company information, view applicants and update application status.

Administrators can manage users, jobs and applications.

## 🚀 Features

### Job Seeker
- Registration and login
- Profile management.
- Search and filter jobs
- Apply for jobs
- Track application status
- Save jobs
- Job alerts

### HR / Recruiter
- HR dashboard
- Create and edit jobs
- Close job postings
- View applicants
- Update application status
- Applicant resume access
- Company profile management
- Work mode selection

### Admin
- Admin dashboard
- Manage users
- View jobs
- View applications

## 🛠️ Technology Stack

### Backend
- Java
- Spring Boot
- Spring MVC
- Spring Data JPA
- Hibernate / JPA

### Frontend
- HTML5
- CSS3
- JavaScript
- Thymeleaf
- Bootstrap 5

### Database
- MySQL

### Tools
- VS Code
- Maven
- Git
- GitHub

## ⚙️ Requirements

- JDK 25
- MySQL 8
- Git

## 🗄️ Database Setup

Create the database in MySQL:

```sql
CREATE DATABASE jobportal;
```

## Social sign-in (Google and GitHub)

The login and registration pages support Google and GitHub OAuth2 sign-in. Provider buttons remain disabled until that provider has both credentials configured; email/password sign-in continues to work without OAuth credentials.

OAuth accounts are associated by provider and the provider's stable user ID. New accounts receive the existing `JOB_SEEKER` role. A verified email is required. If the email already belongs to an account, sign-in is rejected rather than automatically linking or changing that account. The user must sign in with the original method; account linking is not currently offered.

### Provider setup

Create OAuth credentials in each provider's developer console, then register these redirect/callback URLs:

| Environment | Google authorized redirect URI | GitHub callback URL |
|---|---|---|
| Local default port | `http://localhost:8080/login/oauth2/code/google` | `http://localhost:8080/login/oauth2/code/github` |
| Render production | `https://jobportal-ww3c.onrender.com/login/oauth2/code/google` | `https://jobportal-ww3c.onrender.com/login/oauth2/code/github` |

The callbacks use Spring Security's standard `/login/oauth2/code/{registrationId}` endpoint. Google requests the `openid`, `profile`, and `email` scopes. GitHub requests only `user:email` and uses its authenticated email endpoint to require the primary verified address.

### Environment variables

Set these in the local process environment or in Render's **Environment** settings. Do not commit credentials or paste them into application files.

| Variable | Required for |
|---|---|
| `GOOGLE_CLIENT_ID` | Google sign-in |
| `GOOGLE_CLIENT_SECRET` | Google sign-in |
| `GITHUB_CLIENT_ID` | GitHub sign-in |
| `GITHUB_CLIENT_SECRET` | GitHub sign-in |

Each provider is enabled only when both of its variables are non-empty. Incomplete credentials disable only that provider; normal email/password authentication remains available.

### Run locally and deploy

1. For local development, create the `jobportal` database in MySQL and set `DB_USERNAME` and `DB_PASSWORD` for that local server. The local profile defaults to the conventional local MySQL endpoint; set `DB_URL` only when using a different JDBC endpoint. For Render, activate `prod` and set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from the Aiven connection settings.
2. Set either or both OAuth credential pairs in your local environment.
3. Ensure the provider console callback uses the same host and port as the local application (the default is `http://localhost:8080`).
4. Start the app with `.\mvnw.cmd spring-boot:run`.
5. In Render, set the four OAuth variables as needed and set `SPRING_PROFILES_ACTIVE=prod`. The production profile retains Secure session cookies and honors forwarded HTTPS headers so Spring Security generates HTTPS callback URLs.
6. Confirm the Render provider-console callback URLs match the table above. No OAuth secrets are required in source control.

The OAuth registration table is created/updated by the existing JPA schema management. No provider login was tested against Google or GitHub without real provider credentials.

### Verification

Verified for this change: `.\mvnw.cmd test` passed all 86 tests with zero failures or errors, including the existing job-portal workflow/CSRF tests and the OAuth configuration, authorization-entry, and account-provisioning tests. Real provider authentication still requires manual provider-console setup and credentials.
