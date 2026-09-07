CREATE TABLE media_variant_jobs (
    media_id          UUID        PRIMARY KEY REFERENCES media_assets(id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    attempt_count     INTEGER     NOT NULL DEFAULT 0,
    available_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_expires_at  TIMESTAMPTZ,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_variant_jobs_available
    ON media_variant_jobs(status, available_at);

SELECT create_updated_at_trigger('media_variant_jobs');