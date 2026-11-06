CREATE TABLE password_reset_email_outbox (
    id CHAR(36) NOT NULL,
    job_type VARCHAR(24) NOT NULL,
    user_id BIGINT NULL,
    token_id BIGINT NULL,
    encrypted_payload BLOB NULL,
    nonce BINARY(12) NULL,
    encryption_key_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    locked_until TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    CONSTRAINT pk_password_reset_email_outbox PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_email_outbox_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_password_reset_email_outbox_token FOREIGN KEY (token_id)
        REFERENCES password_reset_tokens (id) ON DELETE CASCADE,
    CONSTRAINT ux_password_reset_email_outbox_token UNIQUE (token_id),
    CONSTRAINT ck_password_reset_email_outbox_type
        CHECK (job_type IN ('RESET_REQUEST', 'RESET_EMAIL')),
    CONSTRAINT ck_password_reset_email_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_password_reset_email_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_password_reset_email_outbox_relations CHECK (
        (job_type = 'RESET_REQUEST' AND user_id IS NULL AND token_id IS NULL)
        OR (job_type = 'RESET_EMAIL' AND user_id IS NOT NULL AND token_id IS NOT NULL)
    ),
    CONSTRAINT ck_password_reset_email_outbox_payload CHECK (
        (status IN ('PENDING', 'PROCESSING') AND encrypted_payload IS NOT NULL AND nonce IS NOT NULL)
        OR (status IN ('SENT', 'COMPLETED', 'FAILED') AND encrypted_payload IS NULL AND nonce IS NULL)
    )
);

CREATE INDEX ix_password_reset_email_outbox_claim
    ON password_reset_email_outbox (status, next_attempt_at, locked_until, created_at);
