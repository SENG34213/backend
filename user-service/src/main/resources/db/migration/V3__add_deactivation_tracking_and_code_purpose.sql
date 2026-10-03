ALTER TABLE users
    ADD COLUMN deactivated_by VARCHAR(20);

ALTER TABLE password_reset_tokens
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_RESET';

CREATE INDEX idx_password_reset_tokens_user_purpose
    ON password_reset_tokens(user_id, purpose);