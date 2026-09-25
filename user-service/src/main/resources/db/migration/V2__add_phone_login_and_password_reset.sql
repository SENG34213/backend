-- FR-03: phone number must be unique to be usable as a login identifier.
-- Postgres allows multiple NULLs under a UNIQUE constraint, so users who
-- never supplied a phone number are unaffected.
ALTER TABLE users
    ADD CONSTRAINT uq_users_phone_number UNIQUE (phone_number);

-- FR-04/FR-05: one-time verification codes for password reset via email or phone.
CREATE TABLE password_reset_tokens (
                                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       code_hash     VARCHAR(255) NOT NULL,
                                       channel       VARCHAR(20)  NOT NULL,
                                       expires_at    TIMESTAMPTZ  NOT NULL,
                                       used          BOOLEAN      NOT NULL DEFAULT FALSE,
                                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
