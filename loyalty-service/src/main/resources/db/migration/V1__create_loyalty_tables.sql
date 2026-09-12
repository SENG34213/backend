CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE loyalty_accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL UNIQUE,
    points_balance BIGINT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_points_non_negative CHECK (points_balance >= 0)
);

CREATE TABLE loyalty_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    type                VARCHAR(20) NOT NULL,
    points              BIGINT NOT NULL,
    reference_payment_id UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loyalty_tx_user ON loyalty_transactions(user_id);
