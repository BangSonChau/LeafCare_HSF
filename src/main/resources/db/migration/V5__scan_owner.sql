-- Each scan belongs to the user who made it. Old scans (before login existed) stay NULL: only ADMIN sees them.
ALTER TABLE scan_history ADD COLUMN user_id BIGINT;
ALTER TABLE scan_history ADD CONSTRAINT fk_scan_history_user FOREIGN KEY (user_id) REFERENCES app_user (id);
CREATE INDEX ix_scan_history_user_created ON scan_history (user_id, created_at);
