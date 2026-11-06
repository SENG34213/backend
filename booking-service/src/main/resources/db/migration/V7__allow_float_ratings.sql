-- V7: Allow floating-point / decimal ratings (e.g., 4.5, 3.5, 2.5) for gaming session reviews
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_rating_check;
ALTER TABLE reviews ALTER COLUMN rating TYPE NUMERIC(3, 1);
ALTER TABLE reviews ADD CONSTRAINT reviews_rating_check CHECK (rating >= 1.0 AND rating <= 5.0);

