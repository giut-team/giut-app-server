ALTER TABLE competitions
    ADD INDEX idx_competitions_status_id (publication_status, id),
    ADD INDEX idx_competitions_status_app_end_at (publication_status, application_end_at);
