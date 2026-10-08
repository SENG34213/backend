ALTER TABLE tournaments
    ADD COLUMN participant_count INT NOT NULL DEFAULT 0,
    ADD COLUMN registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

CREATE TABLE brackets (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL UNIQUE REFERENCES tournaments(id),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    bracket_id UUID NOT NULL REFERENCES brackets(id),
    round INT NOT NULL,
    participant1_id UUID NOT NULL,
    participant2_id UUID,
    winner_id UUID,
    status VARCHAR(50) NOT NULL,
    scheduled_time TIMESTAMP WITH TIME ZONE
);
