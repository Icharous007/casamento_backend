-- Normalize existing Brazilian guest phones to the local format XX XXXXX-XXXX.
-- The conflict check prevents changing two identities into the same phone.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT event_id,
                   right(regexp_replace(phone_e164, '[^0-9]', '', 'g'), 11) AS normalized_phone,
                   COUNT(*) AS phone_count
            FROM guests
            WHERE phone_e164 IS NOT NULL
              AND length(regexp_replace(phone_e164, '[^0-9]', '', 'g')) >= 11
            GROUP BY event_id, right(regexp_replace(phone_e164, '[^0-9]', '', 'g'), 11)
            HAVING COUNT(*) > 1
        ) conflicts
    ) THEN
        RAISE EXCEPTION 'Duplicate guest phone after Brazilian normalization';
    END IF;
END $$;

WITH normalized AS (
    SELECT id,
           right(regexp_replace(phone_e164, '[^0-9]', '', 'g'), 11) AS digits
    FROM guests
    WHERE phone_e164 IS NOT NULL
      AND length(regexp_replace(phone_e164, '[^0-9]', '', 'g')) >= 11
)
UPDATE guests AS guest
SET phone_e164 = substring(normalized.digits FROM 1 FOR 2) || ' '
    || substring(normalized.digits FROM 3 FOR 5) || '-'
    || substring(normalized.digits FROM 8 FOR 4)
FROM normalized
WHERE guest.id = normalized.id;