-- =============================================================================
-- V002__seed_data.sql
-- Dados iniciais: evento base + admin padrão
-- Admin padrão: admin@casamento.local / Casamento@2027!
-- IMPORTANTE: Trocar a senha do admin antes de ir para produção!
-- =============================================================================

-- Evento base (ajustar conforme necessidade via admin panel)
INSERT INTO events (
    id,
    slug,
    title,
    couple_names,
    event_date,
    venue_name,
    venue_address,
    rsvp_deadline_at,
    gallery_hide_at,
    status,
    timezone
) VALUES (
    gen_random_uuid(),
    'casamento-2027',
    'Casamento',
    'Noivo & Noiva',
    '2027-01-30 19:00:00-03:00',
    'Local do Evento',
    'Endereço do evento',
    '2027-01-15 23:59:59-03:00',
    '2027-03-01 23:59:59-03:00',
    'DRAFT',
    'America/Sao_Paulo'
);

-- Admin padrão
-- Senha: Casamento2027  (BCrypt cost 12)
-- TROCAR ANTES DE PRODUÇÃO via POST /api/v1/admin/auth/reset-password
INSERT INTO internal_users (
    id,
    name,
    email,
    password_hash,
    role,
    active
) VALUES (
    gen_random_uuid(),
    'Administrador',
    'admin@casamento.local',
    '$2b$12$XuKT9UHJJWji6yghAlJ2/O.2TBC1HGWVYaQCKaxfmYv32.heKVMGO',
    'ADMIN',
    true
);

-- Definições dos jobs agendados
INSERT INTO job_definitions (id, code, description, cron_expression, enabled) VALUES
    (gen_random_uuid(), 'process-email-queue',         'Processa fila de emails pendentes',          '0 9,15,21 * * *', true),
    (gen_random_uuid(), 'prepare-rsvp-reminders',      'Prepara lembretes de RSVP pendentes',        '0 8 * * *',       true),
    (gen_random_uuid(), 'prepare-confirmed-reminders', 'Prepara lembretes para confirmados',          '15 8 * * *',      true),
    (gen_random_uuid(), 'prepare-post-event-thanks',   'Prepara agradecimentos pós-evento',           '30 8 * * *',      true),
    (gen_random_uuid(), 'apply-retention-rules',       'Aplica regras de retenção e arquivamento',   '0 3 * * *',       true);
