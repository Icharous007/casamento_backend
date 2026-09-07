-- Media Error Events table for diagnostics
CREATE TABLE media_error_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id UUID REFERENCES guests(id) ON DELETE SET NULL,
    media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    
    -- Error details (no secrets)
    source VARCHAR(20) NOT NULL, -- CLIENT or SERVER
    stage VARCHAR(50) NOT NULL, -- validation, upload, intent, cors, complete, variant, gallery
    error_code VARCHAR(50) NOT NULL, -- CORS_BLOCKED, UPLOAD_LIMIT_EXCEEDED, etc.
    error_category VARCHAR(30) NOT NULL, -- timeout, auth, network, http, browser, cors, unknown
    error_message VARCHAR(500), -- sanitized; never contains URLs, tokens, or keys
    http_status INTEGER,
    axios_code VARCHAR(50),
    
    -- Context (no signed URLs or PII)
    media_type VARCHAR(10), -- PHOTO or VIDEO
    content_type VARCHAR(100),
    file_size_bytes BIGINT,
    browser_descriptor VARCHAR(200), -- sanitized user agent snippet
    
    -- Timing
    duration_ms INTEGER,
    retryable BOOLEAN DEFAULT TRUE,
    
    -- Correlation
    trace_id VARCHAR(100),
    flow_id VARCHAR(100),
    attempt_id VARCHAR(100),
    client_route VARCHAR(200),
    
    -- Lifecycle
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolution_note VARCHAR(500),
    
    -- Constraints
    CONSTRAINT fk_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT valid_source CHECK (source IN ('CLIENT', 'SERVER')),
    CONSTRAINT valid_category CHECK (error_category IN ('timeout', 'auth', 'network', 'http', 'browser', 'cors', 'unknown'))
);

-- Indexes for common queries
CREATE INDEX idx_media_error_events_event_id ON media_error_events(event_id);
CREATE INDEX idx_media_error_events_guest_id ON media_error_events(guest_id);
CREATE INDEX idx_media_error_events_media_id ON media_error_events(media_id);
CREATE INDEX idx_media_error_events_created_at_desc ON media_error_events(created_at DESC);
CREATE INDEX idx_media_error_events_error_code ON media_error_events(error_code);
CREATE INDEX idx_media_error_events_error_category ON media_error_events(error_category);
CREATE INDEX idx_media_error_events_stage ON media_error_events(stage);
CREATE INDEX idx_media_error_events_unresolved ON media_error_events(resolved_at) WHERE resolved_at IS NULL;
CREATE INDEX idx_media_error_events_trace_id ON media_error_events(trace_id);
CREATE INDEX idx_media_error_events_flow_attempt ON media_error_events(flow_id, attempt_id, error_code, created_at DESC);

-- The service applies the 10-minute deduplication window at query time.
-- A partial index cannot use NOW() because it is not immutable.
CREATE INDEX idx_media_error_events_client_dedupe ON media_error_events(
    event_id, guest_id, attempt_id, error_code, source, created_at
) WHERE source = 'CLIENT';
