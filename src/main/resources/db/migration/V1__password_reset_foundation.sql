ALTER TABLE users
    ADD COLUMN credential_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_digest CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_password_reset_tokens_digest
    ON password_reset_tokens (token_digest);

CREATE INDEX ix_password_reset_tokens_user_expiry
    ON password_reset_tokens (user_id, expires_at);
