-- V3: Add image_url column to game_stations for custom station photo management
ALTER TABLE game_stations ADD COLUMN IF NOT EXISTS image_url TEXT;
