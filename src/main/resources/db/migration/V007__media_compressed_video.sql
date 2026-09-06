-- =============================================================================
-- V007__media_compressed_video.sql
-- Adds a re-encoded (H.264/AAC, scaled down) video key so the gallery serves a
-- much smaller file than the raw upload from the guest's phone. The original
-- stays in r2_key as a backup; this column is only set when compression succeeds.
-- =============================================================================

ALTER TABLE media_assets ADD COLUMN r2_compressed_key VARCHAR(500);
