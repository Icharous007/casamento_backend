CREATE TABLE media_upload_intents (
    id                      UUID        PRIMARY KEY REFERENCES media_assets(id) ON DELETE CASCADE,
    guest_id                UUID        NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    idempotency_key         VARCHAR(128) NOT NULL,
    expected_content_type   VARCHAR(100) NOT NULL,
    expected_file_size      BIGINT      NOT NULL CHECK (expected_file_size > 0),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    expires_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    failure_code            VARCHAR(80),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (guest_id, idempotency_key)
);

CREATE INDEX idx_media_upload_intents_status_expiry
    ON media_upload_intents(status, expires_at);

SELECT create_updated_at_trigger('media_upload_intents');