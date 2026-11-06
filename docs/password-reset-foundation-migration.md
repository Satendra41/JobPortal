# Password reset foundation migration

## Current schema management

The project has no existing Flyway or Liquibase migrations. The common application
configuration uses Hibernate `ddl-auto=update`, and production inherits that setting.
Test configuration uses H2 with `ddl-auto=create-drop`.

Flyway is now opt-in through `FLYWAY_ENABLED`; it defaults to disabled. This prevents
the new dependency from automatically baselining or migrating a database. Production
`ddl-auto` configuration has not been changed.

## Version 1 migration

`V1__password_reset_foundation.sql` adds `users.credential_version` with a database
default of zero and creates `password_reset_tokens`. The token table stores only a
64-character SHA-256 digest, a user foreign key, creation and expiry timestamps,
consumed state, and failed-attempt count. The digest is unique. Reset-token rows are
removed with their user because they are short-lived security artifacts.

## Existing schema baseline requirements

No database was inspected or baselined for this change. Existing local or production
databases are expected to be non-empty and may have schema drift because Hibernate
currently updates them automatically. Before enabling Flyway for an existing database,
an operator must compare its schema with the application schema and deliberately create
Flyway's schema-history baseline. Do not use automatic baseline-on-migrate.

For a database that still has the pre-feature schema, baseline at version 0 so V1 can
apply. If Hibernate has already created the new column and token table, verify that
their definitions match V1 and baseline at version 1 instead; do not rerun V1 over
those objects. A brand-new empty database also needs the existing application schema
created before this additive migration can run, since V1 assumes `users` already
exists. A full initial-schema migration and any production change to schema ownership
require a separate approved rollout plan.

The H2 compatibility test creates an isolated legacy `users` table, baselines only that
in-memory schema at version 0, and applies V1, V2, and V3. It does not connect to or
baseline a real database.

## Version 2 migration: durable request limits

`V2__password_reset_rate_limits.sql` creates `password_reset_rate_limits`, keyed by a
unique `(limit_scope, identifier_hash)` pair. Scope is `ACCOUNT` or `CLIENT`; the record
stores a one-hour window start and request count. It contains no email address or IP
address. Java derives each identifier hash with HMAC-SHA-256 using
`PASSWORD_RESET_RATE_LIMIT_HASH_KEY`; the secret is configuration-only and must be at
least 32 UTF-8 bytes. Rows older than two days are opportunistically removed while
processing a reset request.

The service uses a MySQL `INSERT ... ON DUPLICATE KEY UPDATE` statement. The unique key
serializes concurrent requests for the same scope and identifier, and the update caps
the stored count at the configured limit. H2 tests use MySQL compatibility mode and apply
the migrations only to isolated in-memory databases.

The client limit is reserved before looking up an account. The account limit is reserved
only after a matching user is found, avoiding unbounded rate-limit rows for arbitrary
unknown email addresses. Both paths return the same endpoint response. Defaults are five
requests per account and twenty per client in a one-hour window; operators can change the
maximums through `PASSWORD_RESET_ACCOUNT_MAX_REQUESTS` and
`PASSWORD_RESET_CLIENT_MAX_REQUESTS`.

The application reads only `HttpServletRequest.getRemoteAddr()` and never parses
`X-Forwarded-For` itself. Production keeps Tomcat's native forwarding support but
overrides its internal-proxy pattern with `PASSWORD_RESET_TRUSTED_PROXY_REGEX`, whose
default matches no address (`^$`). Configure that variable with the exact trusted proxy
source addresses/ranges for the hosting environment. With no proxy match, the container
does not accept forwarded address headers and all traffic sharing a proxy address will
share one client quota. Do not broaden this pattern to arbitrary networks. This task did
not inspect Render or determine its proxy ranges.

Production configuration also requires `PASSWORD_RESET_BASE_URL` (HTTPS) and
`PASSWORD_RESET_RATE_LIMIT_HASH_KEY`; neither value is embedded in source. Flyway remains
opt-in and Hibernate production schema management remains unchanged. No real database
was migrated. Enabling V2 for a deployed database requires a separate operational schema
review and rollout.

## Version 3 migration: durable encrypted email outbox

`V3__password_reset_email_outbox.sql` creates `password_reset_email_outbox`. It holds
encrypted `RESET_REQUEST` intake messages and encrypted `RESET_EMAIL` delivery payloads,
with due times, attempts, processing leases, terminal state, and bounded error codes.
User and reset-token references are nullable for intake jobs and required for delivery
jobs. Token IDs are unique. Completing or failing a job clears its ciphertext and nonce.

The Java outbox encrypts payloads with AES-256-GCM. The database stores only ciphertext,
a 12-byte nonce, and a key ID. AAD binds ciphertext to its job ID/type/user/token IDs.
Configure `PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS` as comma-separated
`keyId=Base64(32-byte-key)` values and `PASSWORD_RESET_OUTBOX_ACTIVE_KEY_ID` as the
active ID. Keep old decryption keys configured until all jobs encrypted with them have
reached a terminal state; key rotation is not automatic.

The controller persists a validly formatted request to the outbox before returning its
generic response. Account lookup, account limiting, token issuance, and insertion of the
matching email job happen in one transaction. The worker claims jobs with an atomic
compare-and-set update and a two-minute lease. A stopped worker's expired claims can be
reclaimed. Failures retry with exponential backoff (30 seconds up to one hour), for at
most eight attempts. This provides durable, at-least-once delivery attempts, not
exactly-once email delivery: a provider can accept a message while its response is lost,
so a retry can send a duplicate email.

## Reset request, delivery, and redemption

`POST /users/password-reset/request` and the `POST /forgot-password` form return the same
generic acceptance behavior for known, unknown, disabled, unverified, rate-limited, and
delivery-failure cases. Client limiting and durable encrypted request queuing happen
before the response; account lookup and email delivery run in the worker. The worker and
controller log fixed messages and exception classes only; payload `toString()` methods
redact contents.

The reset URL is built from `PASSWORD_RESET_BASE_URL`, never from the request host. In
production it must be HTTPS and use a DNS hostname, with no credentials, query, or
fragment. The `GET /reset-password?token=...` page validates without consuming the token;
POST revalidates and redeems it transactionally, confirms the password, applies the shared
password policy, BCrypt-encodes the password, consumes the token, increments
`credential_version`, and invalidates the current session. Other sessions are invalidated
on their next request by comparing credential versions.

The reset pages set no-store, no-referrer, and noindex headers and load no third-party
assets. The token remains in the query string as required by the email contract; hosting
and reverse-proxy access logs must redact the `token` query parameter before deployment.
That hosting configuration has not been verified here.

Production reuses `OTP_EMAIL_API_URL` and `OTP_EMAIL_API_SECRET` with a separate Apps
Script payload `messageType=password_reset`. Registration and email-change OTP contracts
are unchanged. `PASSWORD_RESET_ALLOWED_HOSTS` in Apps Script must include the exact
hostname used by `PASSWORD_RESET_BASE_URL`. Local profile uses the separate SMTP reset
sender and requires the existing mail configuration to actually deliver mail.

Production configuration requires `PASSWORD_RESET_BASE_URL` (HTTPS),
`PASSWORD_RESET_RATE_LIMIT_HASH_KEY`, `PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS`, and
`PASSWORD_RESET_OUTBOX_ACTIVE_KEY_ID`; none is embedded in source. Flyway remains
opt-in and Hibernate production schema management remains unchanged. V3 was tested only
against isolated H2. No real database was migrated. Enabling Flyway on a deployed
database requires a separate schema comparison, baseline decision, backup, and approved
rollout plan.
