ALTER TABLE owners
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE auth_rate_limit_buckets (
    bucket_key CHAR(64) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    request_count INT UNSIGNED NOT NULL,
    PRIMARY KEY (bucket_key),
    KEY idx_auth_rate_limit_window (window_started_at)
);
