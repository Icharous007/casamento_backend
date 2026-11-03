ALTER TABLE events
    ADD COLUMN pix_key VARCHAR(150) NOT NULL DEFAULT '00115205152',
    ADD COLUMN pix_key_type VARCHAR(20) NOT NULL DEFAULT 'CPF',
    ADD COLUMN pix_merchant_name VARCHAR(25),
    ADD COLUMN pix_merchant_city VARCHAR(15);

ALTER TABLE gift_items
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'WEDDING'
        CHECK (category IN ('WEDDING', 'BRIDAL_SHOWER')),
    ADD COLUMN price_amount NUMERIC(10, 2)
        CHECK (price_amount IS NULL OR price_amount > 0);

ALTER TABLE gift_purchase_marks
    DROP CONSTRAINT IF EXISTS gift_purchase_marks_gift_item_id_key,
    ALTER COLUMN guest_id DROP NOT NULL,
    ADD COLUMN giver_name VARCHAR(255),
    ADD COLUMN giver_phone VARCHAR(30),
    ADD COLUMN giver_message TEXT,
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (payment_method IN ('MANUAL', 'PIX')),
    ADD COLUMN paid_amount NUMERIC(10, 2),
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'
        CHECK (payment_status IN ('UNVERIFIED', 'REPORTED'));

UPDATE gift_items
SET status = 'AVAILABLE'
WHERE status = 'PURCHASED';

CREATE INDEX idx_gift_items_event_category
    ON gift_items(event_id, category, visible_to_guests, display_order);

CREATE INDEX idx_gift_marks_item_purchased_at
    ON gift_purchase_marks(gift_item_id, purchased_at DESC);