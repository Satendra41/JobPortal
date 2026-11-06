CREATE TABLE password_reset_rate_limits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    limit_scope VARCHAR(16) NOT NULL,
    identifier_hash CHAR(64) NOT NULL,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INTEGER NOT NULL,
    CONSTRAINT pk_password_reset_rate_limits PRIMARY KEY (id),
    CONSTRAINT ck_password_reset_rate_limits_scope CHECK (limit_scope IN ('ACCOUNT', 'CLIENT')),
    CONSTRAINT ck_password_reset_rate_limits_count CHECK (request_count > 0),
    CONSTRAINT ux_password_reset_rate_limits_scope_hash UNIQUE (limit_scope, identifier_hash)
);

CREATE INDEX ix_password_reset_rate_limits_window
    ON password_reset_rate_limits (window_started_at);
