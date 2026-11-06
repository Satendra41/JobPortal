# Reconstructed JAGAQ Apps Script email endpoint

`Code.gs` is a **reconstruction draft**, not recovered original source. It has not been deployed or executed. The repository's Java client establishes the legacy OTP request and response shape, but the original Apps Script quota values and email wording were unavailable. The OTP defaults below are new draft assumptions and should be reviewed against operational needs before deployment.

## Request contracts

The legacy registration OTP request remains unchanged:

```json
{"email":"candidate@example.com","otp":"123456","secret":"..."}
```

The new password-reset request is separate and explicit:

```json
{"messageType":"password_reset","email":"candidate@example.com","resetUrl":"https://your-approved-host/reset-password?token=...","secret":"..."}
```

Requests without `messageType` continue through the OTP path. Password-reset delivery does not look up accounts, so the script response only reports transport acceptance and does not disclose whether an account exists. The application must return a generic response to the requester regardless of whether an account was eligible for reset.

Both operations return JSON. Success is `{"success":true}`. Failures use `{"success":false,"error":"<generic code>"}`. The existing Java sender requires a boolean `success` field on a 2xx response; it does not rely on a particular error value.

## Script Properties

Set these in **Apps Script → Project Settings → Script Properties**. Do not place secret values in source control or source code.

| Property | Required | Purpose |
| --- | --- | --- |
| `OTP_EMAIL_API_SECRET` | Yes | Shared secret compared with the `secret` request field. Use the same value configured for the Java sender. |
| `PASSWORD_RESET_ALLOWED_HOSTS` | For reset links | Comma-separated exact hostnames allowed in reset URLs, without scheme or path. Restricts emailed links to the application’s approved HTTPS host. |
| `JAGAQ_APP_NAME` | No | Product name used in message copy; defaults to `JAGAQ`. |
| `OTP_EXPIRATION_MINUTES` | No | Display-only OTP expiry in email; defaults to 10 minutes. The application remains responsible for enforcing OTP expiry. |
| `OTP_MAX_PER_EMAIL_PER_HOUR` | No | Draft per-recipient OTP rate limit; defaults to 5 per hour. |
| `RESET_MAX_PER_EMAIL_PER_HOUR` | No | Draft per-recipient reset-email rate limit; defaults to 3 per hour. |

The script also checks `MailApp.getRemainingDailyQuota()` before sending. Google's account-level recipient quota is still authoritative. Per-recipient rate limits use short-lived cache entries keyed by a digest of the normalized email address, not the address itself. Cache eviction can make this application-level limit best-effort; the Google daily quota remains the hard platform limit. These quota and rate-limit defaults are reconstructed assumptions, not the missing original settings.

## Deployment settings (documentation only)

- Deploy as a **Web app**, executing as the script owner so `MailApp` can send from the configured Google account.
- The server-to-server Java client needs unauthenticated Web App access to reach `doPost`; the shared secret is the application-level request check. Review the organization’s current Google deployment access options before deployment.
- Use the HTTPS `/exec` deployment URL in the Java environment configuration. Do not put it or the shared secret in this draft.
- Keep a separate test deployment for non-production validation. Do not test by sending real email to users.
- Update the existing production Apps Script deployment only after verifying both contracts with mocked/test recipients and confirming its behavior with the service owner. This draft itself has not been deployed or executed.

## Security and compatibility notes

- The legacy OTP request keys remain `email`, `otp`, and `secret`; no operation field is required for old OTP callers.
- Password reset uses `messageType=password_reset` and a separate `resetUrl` field. The reset URL must be HTTPS and its hostname must exactly match an allowlisted host.
- Reset URL validation accepts only HTTPS, an exact allowlisted ASCII DNS hostname, a path ending in `/reset-password`, and exactly one 43-character URL-safe `token` query value. It rejects whitespace/control characters, backslashes, malformed percent escapes, user information, ports, fragments, arbitrary paths, and extra query parameters. OTP validation and message wording are unchanged by this validator update.
- The script intentionally does not log requests, message bodies, OTPs, reset URLs, tokens, shared secrets, or exception details.
- The script does not determine whether an account exists. The application must not turn delivery results into an account-existence signal.
- The OTP validity period and reset-token validity must be enforced server-side by the application, not by email copy.
