-- Uploaded leaf photos live in the DB: Render Free wipes the server disk on every deploy/restart.
CREATE TABLE image_file (
    name         VARCHAR(64) PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    data         BYTEA       NOT NULL
);
