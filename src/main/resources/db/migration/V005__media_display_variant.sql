-- =============================================================================
-- V005__media_display_variant.sql
-- Adds a mid-size "display" variant key alongside the existing thumb key,
-- so the guest gallery can avoid loading full-resolution originals.
-- =============================================================================

ALTER TABLE media_assets ADD COLUMN r2_display_key VARCHAR(500);
