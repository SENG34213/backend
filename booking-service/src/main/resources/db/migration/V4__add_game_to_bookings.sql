ALTER TABLE bookings ADD COLUMN game_id UUID REFERENCES games(id);
CREATE INDEX idx_bookings_game_id ON bookings(game_id);