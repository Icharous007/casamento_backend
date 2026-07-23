-- =============================================================================
-- V004__remove_email_whatsapp.sql
-- Removes WhatsApp OTP login, WhatsApp message logging, and admin password
-- reset (email). Guest self-registration is now direct: phone + name +
-- accepted terms, with no OTP/verification step.
-- =============================================================================

-- =============================================================================
-- 1. Drop WhatsApp OTP / message-log tables
-- =============================================================================
DROP TABLE IF EXISTS whatsapp_message_logs CASCADE;
DROP TABLE IF EXISTS guest_login_challenges CASCADE;

-- =============================================================================
-- 2. Drop admin password-reset table (email-based flow removed)
-- =============================================================================
DROP TABLE IF EXISTS password_reset_tokens CASCADE;

-- =============================================================================
-- 3. Drop unused WhatsApp-related columns from guests
-- =============================================================================
ALTER TABLE guests
    DROP COLUMN IF EXISTS phone_verified_at,
    DROP COLUMN IF EXISTS whatsapp_opt_in_at,
    DROP COLUMN IF EXISTS invite_sent_at;

-- =============================================================================
-- 4. Remove obsolete scheduled job definitions (email/WhatsApp queues, reminders)
-- =============================================================================
DELETE FROM job_definitions
WHERE code IN (
    'process-email-queue',
    'process-whatsapp-queue',
    'prepare-rsvp-reminders',
    'prepare-confirmed-reminders',
    'prepare-post-event-thanks'
);
