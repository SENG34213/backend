-- V5: Add image_url column to tournaments table for poster/banner management
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS image_url TEXT;
