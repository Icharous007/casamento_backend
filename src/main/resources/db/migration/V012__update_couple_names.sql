-- Update the production event name without changing the already-applied seed migration.
UPDATE events
SET couple_names = 'Gustavo & Maria Luiza'
WHERE slug = 'casamento-2027'
  AND couple_names = 'Noivo & Noiva';
