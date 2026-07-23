-- =============================================================================
-- V003__phone_whatsapp_guest_access.sql
-- Migrates guest identity from email to phone (E.164).
-- Adds WhatsApp OTP login challenge table and outbound message log.
-- Admin email login is NOT changed.
-- =============================================================================

-- =============================================================================
-- 1. Add phone identity columns to guests
-- =============================================================================
ALTER TABLE guests
    ADD COLUMN IF NOT EXISTS phone_e164          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS phone_verified_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS source              VARCHAR(30)  NOT NULL DEFAULT 'IMPORTED',
    ADD COLUMN IF NOT EXISTS invite_sent_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS whatsapp_opt_in_at  TIMESTAMPTZ;

-- One phone number per event (partial – phone may be null for old records)
CREATE UNIQUE INDEX IF NOT EXISTS idx_guests_phone_event
    ON guests(event_id, phone_e164)
    WHERE phone_e164 IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_guests_phone_e164 ON guests(phone_e164);

-- =============================================================================
-- 2. guest_login_challenges  (WhatsApp OTP flow)
-- =============================================================================
CREATE TABLE IF NOT EXISTS guest_login_challenges (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    -- May be null for first-time self-registered guests until OTP succeeds
    guest_id        UUID        REFERENCES guests(id) ON DELETE CASCADE,
    phone_e164      VARCHAR(20)  NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    code_hash       VARCHAR(255) NOT NULL,
    -- Number of wrong attempts
    attempts        INTEGER      NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_guest_challenges_event_phone
    ON guest_login_challenges(event_id, phone_e164);
CREATE INDEX IF NOT EXISTS idx_guest_challenges_guest_id
    ON guest_login_challenges(guest_id)
    WHERE guest_id IS NOT NULL;

-- =============================================================================
-- 3. whatsapp_message_logs  (outbound message tracking)
-- =============================================================================
CREATE TABLE IF NOT EXISTS whatsapp_message_logs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    guest_id            UUID        REFERENCES guests(id) ON DELETE SET NULL,
    phone_e164          VARCHAR(20)  NOT NULL,
    message_type        VARCHAR(30)  NOT NULL,  -- INVITE, OTP, RSVP_CONFIRMATION, REMINDER
    template_name       VARCHAR(255),
    whatsapp_message_id VARCHAR(255),           -- wamid returned by Cloud API
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, SENT, DELIVERED, READ, FAILED
    error_payload       TEXT,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wa_logs_guest_id ON whatsapp_message_logs(guest_id)
    WHERE guest_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wa_logs_event_id ON whatsapp_message_logs(event_id);
CREATE INDEX IF NOT EXISTS idx_wa_logs_whatsapp_id ON whatsapp_message_logs(whatsapp_message_id)
    WHERE whatsapp_message_id IS NOT NULL;

SELECT create_updated_at_trigger('whatsapp_message_logs');

-- =============================================================================
-- 4. guest_profiles – phone is now the identity; email stays for legacy data
--    but is no longer required or exposed in guest-facing flows.
--    Drop the unique index on email so it does not block null rows on upsert.
-- =============================================================================
-- The index was created with WHERE email IS NOT NULL, so NULL rows are fine.
-- We leave the column and index in place; the application layer simply stops
-- writing or validating email for guests.

-- =============================================================================
-- 5. Seed job_definitions entry for WhatsApp outbound processing
-- =============================================================================
INSERT INTO job_definitions (id, code, description, cron_expression, enabled)
SELECT gen_random_uuid(), 'process-whatsapp-queue', 'Processa fila de mensagens WhatsApp pendentes', '0 */5 * * * *', true
WHERE NOT EXISTS (SELECT 1 FROM job_definitions WHERE code = 'process-whatsapp-queue');
