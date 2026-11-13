CREATE TABLE gallery_feed_sessions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id UUID NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    sort VARCHAR(10) NOT NULL,
    media_ids UUID[] NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_gallery_feed_expiry ON gallery_feed_sessions(expires_at);
