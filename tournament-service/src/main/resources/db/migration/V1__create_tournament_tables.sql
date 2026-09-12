CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tournaments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150) NOT NULL,
    game_title          VARCHAR(100) NOT NULL,
    start_date          TIMESTAMPTZ NOT NULL,
    end_date            TIMESTAMPTZ,
    entry_fee           NUMERIC(10,2) NOT NULL,
    max_participants    INT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    created_by_admin_id UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tournament_registrations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id  UUID NOT NULL REFERENCES tournaments(id),
    user_id        UUID NOT NULL,
    status         VARCHAR(30) NOT NULL,
    payment_id     UUID,
    registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tournament_id, user_id)
);

CREATE INDEX idx_registrations_tournament ON tournament_registrations(tournament_id);
CREATE INDEX idx_registrations_user ON tournament_registrations(user_id);
