CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE game_stations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_code VARCHAR(20)  NOT NULL UNIQUE,
    type         VARCHAR(20)  NOT NULL,
    hourly_rate  NUMERIC(10,2) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE bookings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    station_id  UUID NOT NULL REFERENCES game_stations(id),
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL,
    source      VARCHAR(20) NOT NULL,
    payment_id  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_time CHECK (end_time > start_time)
);

CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_station_time ON bookings(station_id, start_time, end_time);

-- a handful of seed stations so Sprint 6's availability screen has something to show
INSERT INTO game_stations (station_code, type, hourly_rate) VALUES
    ('PC-01', 'PC', 300.00),
    ('PC-02', 'PC', 300.00),
    ('CONSOLE-01', 'CONSOLE', 400.00),
    ('VR-01', 'VR', 600.00);
