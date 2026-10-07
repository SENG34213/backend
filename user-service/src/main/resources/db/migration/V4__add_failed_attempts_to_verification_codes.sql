-- Brute-force protection for 6-digit verification codes (password reset / reactivation):
-- each wrong guess is counted and the code is burned after the limit is reached.
ALTER TABLE password_reset_tokens
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;