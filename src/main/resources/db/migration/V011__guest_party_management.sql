-- =============================================================================
-- V011__guest_party_management.sql
-- Adds support for managing party members (dependents/proxies).
-- Allows a guest to confirm RSVP on behalf of family members.
-- =============================================================================

-- =============================================================================
-- 1. Extend guests table: party management fields
-- =============================================================================
ALTER TABLE guests
    ADD COLUMN IF NOT EXISTS managed_by_guest_id  UUID REFERENCES guests(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS guest_type           VARCHAR(10) NOT NULL DEFAULT 'ADULT'
                                                       CHECK (guest_type IN ('ADULT','CHILD')),
    ADD COLUMN IF NOT EXISTS age                  SMALLINT CHECK (age IS NULL OR (age >= 0 AND age <= 120));

-- Index for efficient lookup of guests managed by a specific guest
CREATE INDEX IF NOT EXISTS idx_guests_managed_by_guest_id
    ON guests(managed_by_guest_id);

-- Index for efficient lookup of guests by type (for buffet/logistics)
CREATE INDEX IF NOT EXISTS idx_guests_type
    ON guests(event_id, guest_type);

-- =============================================================================
-- 2. Extend rsvps table: audit trail for proxy confirmations
-- =============================================================================
ALTER TABLE rsvps
    ADD COLUMN IF NOT EXISTS confirmed_by_guest_id  UUID REFERENCES guests(id) ON DELETE SET NULL;

-- Index for efficient lookup of RSVPs confirmed by a specific guest (for audit/reporting)
CREATE INDEX IF NOT EXISTS idx_rsvps_confirmed_by_guest_id
    ON rsvps(confirmed_by_guest_id);

-- =============================================================================
-- 3. Constraint: a guest cannot manage themselves (data consistency)
-- =============================================================================
ALTER TABLE guests
    ADD CONSTRAINT chk_managed_by_not_self
    CHECK (managed_by_guest_id IS NULL OR managed_by_guest_id != id);

-- =============================================================================
-- Note: Managed guests cannot self-register while managed_by_guest_id is set.
-- When a managed guest self-registers, the registration flow must clear
-- managed_by_guest_id and set source='SELF_REGISTERED' (upsert logic).
-- =============================================================================
