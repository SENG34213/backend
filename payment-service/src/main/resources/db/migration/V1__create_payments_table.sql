CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL,
    reference_type   VARCHAR(30) NOT NULL,
    reference_id     UUID NOT NULL,
    amount           NUMERIC(10,2) NOT NULL,
    method           VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    receipt_number   VARCHAR(50),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_reference ON payments(reference_type, reference_id);
