ALTER TABLE loyalty_accounts
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS lifetime_earned BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE loyalty_transactions
    ALTER COLUMN type TYPE VARCHAR(30),
    ADD COLUMN IF NOT EXISTS booking_id UUID,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN IF NOT EXISTS balance_after BIGINT,
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS description VARCHAR(255);

CREATE TABLE IF NOT EXISTS loyalty_rules (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount_per_point           NUMERIC(12,2) NOT NULL DEFAULT 100.00,
    point_value_lkr           NUMERIC(12,2) NOT NULL DEFAULT 0.50,
    min_redeem_points         BIGINT NOT NULL DEFAULT 100,
    redeem_step               BIGINT NOT NULL DEFAULT 10,
    max_discount_percent      BIGINT NOT NULL DEFAULT 50,
    reservation_timeout_minutes BIGINT NOT NULL DEFAULT 15,
    silver_threshold          BIGINT NOT NULL DEFAULT 1000,
    silver_multiplier         NUMERIC(12,4) NOT NULL DEFAULT 1.25,
    gold_threshold            BIGINT NOT NULL DEFAULT 3000,
    gold_multiplier           NUMERIC(12,4) NOT NULL DEFAULT 1.50,
    updated_by                VARCHAR(100),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_loyalty_tx_user_created_desc
    ON loyalty_transactions (user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loyalty_tx_booking_type_active
    ON loyalty_transactions (booking_id, type)
    WHERE booking_id IS NOT NULL AND status <> 'RELEASED';
