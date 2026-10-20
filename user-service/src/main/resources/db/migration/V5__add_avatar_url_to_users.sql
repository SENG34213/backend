-- V5: Add avatar_url column to users table for profile picture management
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;
