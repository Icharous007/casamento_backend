-- =============================================================================
-- V001__initial_schema.sql
-- Schema inicial da plataforma de casamento
-- Ordem: respeita dependências de FK
-- Nota: gen_random_uuid() é nativo no PostgreSQL 13+ (sem extensão)
-- =============================================================================

-- =============================================================================
-- FUNÇÃO: updated_at automático
-- =============================================================================
CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Macro para criar trigger updated_at
CREATE OR REPLACE FUNCTION create_updated_at_trigger(table_name TEXT)
RETURNS VOID AS $$
BEGIN
    EXECUTE format(
        'CREATE TRIGGER set_updated_at BEFORE UPDATE ON %I
         FOR EACH ROW EXECUTE FUNCTION trigger_set_updated_at()',
        table_name
    );
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 1. EVENTS
-- =============================================================================
CREATE TABLE events (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(100) NOT NULL UNIQUE,
    title               VARCHAR(255) NOT NULL,
    couple_names        VARCHAR(255) NOT NULL,
    event_date          TIMESTAMPTZ NOT NULL,
    venue_name          VARCHAR(255),
    venue_address       TEXT,
    extra_info          TEXT,
    rsvp_deadline_at    TIMESTAMPTZ,
    gallery_hide_at     TIMESTAMPTZ,
    deletion_eligible_at TIMESTAMPTZ,
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT','PUBLISHED','ACTIVE','POST_EVENT','ARCHIVED','DELETION_PENDING','DELETED')),
    timezone            VARCHAR(50)  NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

SELECT create_updated_at_trigger('events');

-- =============================================================================
-- 2. INTERNAL_USERS (admin + cerimonialista)
-- =============================================================================
CREATE TABLE internal_users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','CERIMONIALISTA')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

SELECT create_updated_at_trigger('internal_users');

-- =============================================================================
-- 3. REFRESH_TOKENS
-- =============================================================================
CREATE TABLE refresh_tokens (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES internal_users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- =============================================================================
-- 4. PASSWORD_RESET_TOKENS
-- =============================================================================
CREATE TABLE password_reset_tokens (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES internal_users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pwd_reset_tokens_user_id ON password_reset_tokens(user_id);

-- =============================================================================
-- 5. GUESTS
-- =============================================================================
CREATE TABLE guests (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'INVITED'
                        CHECK (status IN ('INVITED','ACTIVE','BLOCKED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guests_event_id ON guests(event_id);
CREATE INDEX idx_guests_status ON guests(event_id, status);

SELECT create_updated_at_trigger('guests');

-- =============================================================================
-- 6. GUEST_ACCESS_TOKENS
-- =============================================================================
CREATE TABLE guest_access_tokens (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id        UUID        NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guest_tokens_guest_id ON guest_access_tokens(guest_id);
CREATE INDEX idx_guest_tokens_hash ON guest_access_tokens(token_hash);

-- =============================================================================
-- 7. GUEST_PROFILES
-- =============================================================================
CREATE TABLE guest_profiles (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id            UUID        NOT NULL UNIQUE REFERENCES guests(id) ON DELETE CASCADE,
    display_name        VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    dietary_restrictions TEXT,
    allergies           TEXT,
    accepted_terms      BOOLEAN     NOT NULL DEFAULT FALSE,
    accepted_terms_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Email único por convidado (guest_id já é UNIQUE, garantindo 1 perfil por convidado)
CREATE UNIQUE INDEX idx_guest_profiles_email ON guest_profiles(email) WHERE email IS NOT NULL;

SELECT create_updated_at_trigger('guest_profiles');

-- =============================================================================
-- 8. RSVPS
-- =============================================================================
CREATE TABLE rsvps (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id            UUID        NOT NULL UNIQUE REFERENCES guests(id) ON DELETE CASCADE,
    event_id            UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    response            VARCHAR(20) NOT NULL CHECK (response IN ('ATTENDING','DECLINED')),
    dietary_restrictions TEXT,
    allergies           TEXT,
    additional_info     TEXT,
    responded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rsvps_event_id ON rsvps(event_id);
CREATE INDEX idx_rsvps_response ON rsvps(event_id, response);

SELECT create_updated_at_trigger('rsvps');

-- =============================================================================
-- 9. GIFT_ITEMS
-- =============================================================================
CREATE TABLE gift_items (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    external_url    TEXT,
    image_url       TEXT,
    price_range     VARCHAR(50),
    display_order   INT         NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                        CHECK (status IN ('AVAILABLE','PURCHASED','UNAVAILABLE')),
    visible_to_guests BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gift_items_event_id ON gift_items(event_id, status);

SELECT create_updated_at_trigger('gift_items');

-- =============================================================================
-- 10. GIFT_PURCHASE_MARKS
-- =============================================================================
CREATE TABLE gift_purchase_marks (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    gift_item_id    UUID        NOT NULL UNIQUE REFERENCES gift_items(id) ON DELETE CASCADE,
    guest_id        UUID        NOT NULL REFERENCES guests(id) ON DELETE SET NULL,
    purchased_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gift_marks_guest_id ON gift_purchase_marks(guest_id);

-- =============================================================================
-- 11. MEDIA_ASSETS
-- =============================================================================
CREATE TABLE media_assets (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id        UUID        REFERENCES guests(id) ON DELETE SET NULL,
    media_type      VARCHAR(10) NOT NULL CHECK (media_type IN ('PHOTO','VIDEO')),
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING'
                        CHECK (status IN ('PROCESSING','ACTIVE','HIDDEN','DELETED')),
    r2_key          VARCHAR(500) NOT NULL,
    r2_thumb_key    VARCHAR(500),
    original_filename VARCHAR(255),
    content_type    VARCHAR(100),
    file_size_bytes BIGINT,
    like_count      INT         NOT NULL DEFAULT 0,
    comment_count   INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_event_status ON media_assets(event_id, status);
CREATE INDEX idx_media_event_likes ON media_assets(event_id, like_count DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_media_guest_id ON media_assets(guest_id);

SELECT create_updated_at_trigger('media_assets');

-- =============================================================================
-- 12. MEDIA_LIKES
-- =============================================================================
CREATE TABLE media_likes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID        NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    guest_id        UUID        NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (media_id, guest_id)
);

CREATE INDEX idx_media_likes_media_id ON media_likes(media_id);

-- =============================================================================
-- 13. MEDIA_COMMENTS
-- =============================================================================
CREATE TABLE media_comments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID        NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    guest_id        UUID        NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    content         TEXT        NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','REMOVED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_comments_media_id ON media_comments(media_id, status);

SELECT create_updated_at_trigger('media_comments');

-- =============================================================================
-- 14. WALL_POSTS
-- =============================================================================
CREATE TABLE wall_posts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id        UUID        NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    post_type       VARCHAR(10) NOT NULL CHECK (post_type IN ('TEXT','AUDIO')),
    content         TEXT,
    r2_audio_key    VARCHAR(500),
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','REMOVED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wall_posts_event_id ON wall_posts(event_id, status, created_at DESC);

SELECT create_updated_at_trigger('wall_posts');

-- =============================================================================
-- 15. EMAIL_QUEUE_ITEMS
-- =============================================================================
CREATE TABLE email_queue_items (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id            UUID        REFERENCES guests(id) ON DELETE SET NULL,
    email_type          VARCHAR(30) NOT NULL
                            CHECK (email_type IN ('INVITE','RSVP_REMINDER','RSVP_CONFIRMATION','EVENT_REMINDER','POST_EVENT_THANKS')),
    recipient_email     VARCHAR(255) NOT NULL,
    recipient_name      VARCHAR(255),
    template_variables  JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PENDING_APPROVAL','PROCESSING','SENT','FAILED','CANCELED')),
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    batch_key           VARCHAR(100),
    scheduled_for       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempt_count       INT         NOT NULL DEFAULT 0,
    last_error          TEXT,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_queue_status ON email_queue_items(status, scheduled_for);
CREATE INDEX idx_email_queue_batch ON email_queue_items(batch_key) WHERE batch_key IS NOT NULL;
CREATE INDEX idx_email_queue_event ON email_queue_items(event_id, email_type);

SELECT create_updated_at_trigger('email_queue_items');

-- =============================================================================
-- 16. JOB_DEFINITIONS
-- =============================================================================
CREATE TABLE job_definitions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(60) NOT NULL UNIQUE,
    description     VARCHAR(255),
    cron_expression VARCHAR(60),
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- 17. JOB_EXECUTIONS
-- =============================================================================
CREATE TABLE job_executions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_code        VARCHAR(60) NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
                        CHECK (status IN ('RUNNING','COMPLETED','FAILED','SKIPPED')),
    triggered_by    VARCHAR(30) NOT NULL DEFAULT 'SCHEDULER'
                        CHECK (triggered_by IN ('SCHEDULER','ADMIN','SYSTEM')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    items_processed INT         NOT NULL DEFAULT 0,
    items_failed    INT         NOT NULL DEFAULT 0,
    error_message   TEXT,
    metadata        JSONB
);

CREATE INDEX idx_job_executions_code ON job_executions(job_code, started_at DESC);
CREATE INDEX idx_job_executions_status ON job_executions(status);

-- =============================================================================
-- 18. JOB_LOCKS (prevent concurrent critical job execution)
-- =============================================================================
CREATE TABLE job_locks (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_code        VARCHAR(60) NOT NULL UNIQUE,
    locked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    lock_holder     VARCHAR(100)
);

CREATE INDEX idx_job_locks_expires ON job_locks(job_code, expires_at);

-- =============================================================================
-- 19. ADMIN_ACTIONS (audit trail)
-- =============================================================================
CREATE TABLE admin_actions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        REFERENCES internal_users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50),
    target_id       VARCHAR(100),
    details         JSONB,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_actions_user_id ON admin_actions(user_id);
CREATE INDEX idx_admin_actions_created ON admin_actions(created_at DESC);
CREATE INDEX idx_admin_actions_action ON admin_actions(action);

-- =============================================================================
-- 20. EVENT_RETENTION_STATES
-- =============================================================================
CREATE TABLE event_retention_states (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                UUID        NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    gallery_hidden_at       TIMESTAMPTZ,
    uploads_blocked_at      TIMESTAMPTZ,
    archive_generation_at   TIMESTAMPTZ,
    deletion_requested_at   TIMESTAMPTZ,
    deletion_confirmed_at   TIMESTAMPTZ,
    deletion_phrase_used    VARCHAR(255),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

SELECT create_updated_at_trigger('event_retention_states');

-- =============================================================================
-- 21. FINAL_ARCHIVE_PACKAGES
-- =============================================================================
CREATE TABLE final_archive_packages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'GENERATING'
                        CHECK (status IN ('GENERATING','READY','FAILED')),
    r2_key          VARCHAR(500),
    file_size_bytes BIGINT,
    generated_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

SELECT create_updated_at_trigger('final_archive_packages');

-- =============================================================================
-- Índices adicionais de performance
-- =============================================================================
CREATE INDEX idx_guests_event_search ON guests(event_id, name);
CREATE INDEX idx_media_created ON media_assets(event_id, created_at DESC) WHERE status = 'ACTIVE';
