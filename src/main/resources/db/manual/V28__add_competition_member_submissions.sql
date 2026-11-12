-- Apply to existing MySQL databases before deploying member submissions.
ALTER TABLE competitions
    ADD COLUMN submission_source_url TEXT NULL,
    ADD COLUMN submission_source_url_hash VARCHAR(64) NULL,
    ADD COLUMN submitted_by_user_id BIGINT NULL,
    ADD CONSTRAINT uk_competitions_submission_url_hash UNIQUE (submission_source_url_hash),
    ADD CONSTRAINT fk_competitions_submitted_by_user FOREIGN KEY (submitted_by_user_id) REFERENCES users(id);

CREATE INDEX idx_competition_urls_normalized_hash ON competition_urls (normalized_url_hash);
