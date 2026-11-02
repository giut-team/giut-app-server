-- Run with application writes stopped, after V21 or verifying that the column exists.
UPDATE competitions SET activity_type = 'COMPETITION' WHERE activity_type IS NULL;
ALTER TABLE competitions MODIFY COLUMN activity_type VARCHAR(30) NOT NULL DEFAULT 'COMPETITION';
