CREATE TABLE IF NOT EXISTS compensating_transactions (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload TEXT,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comp_tx_booking_id ON compensating_transactions(booking_id);
CREATE INDEX IF NOT EXISTS idx_comp_tx_status ON compensating_transactions(status);
