-- Existing rows remain NULL until their activity type is verified.
ALTER TABLE competitions ADD COLUMN activity_type VARCHAR(30) NULL;
