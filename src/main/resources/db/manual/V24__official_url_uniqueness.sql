-- MySQL 8.4; stop application writes and back up the database before running.
-- MySQL DDL commits implicitly: review each error before retrying.
-- Preflight: the legacy index must be exactly one unique single-column index
-- on normalized_url_hash; inspect SHOW INDEX FROM competition_urls first.
DELIMITER //
CREATE PROCEDURE giut_migrate_official_url_uniqueness_v24()
BEGIN
    DECLARE legacy_count INT DEFAULT 0;
    DECLARE legacy_name VARCHAR(64);
    DECLARE already_exists INT DEFAULT 0;

    SELECT COUNT(*), MIN(index_name) INTO legacy_count, legacy_name
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'competition_urls'
          AND non_unique = 0 AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'normalized_url_hash'
    ) AS legacy_indexes;
    SELECT COUNT(*) INTO already_exists FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'competition_urls' AND column_name = 'official_url_hash';
    IF legacy_count <> 1 OR already_exists <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected schema: inspect competition_urls indexes/columns before migration';
    END IF;

    ALTER TABLE competition_urls
        ADD COLUMN official_url_hash VARCHAR(64)
            GENERATED ALWAYS AS (CASE WHEN url_type = 'OFFICIAL' THEN normalized_url_hash ELSE NULL END) VIRTUAL,
        ADD CONSTRAINT uk_competition_urls_official_hash UNIQUE (official_url_hash),
        ADD CONSTRAINT uk_competition_urls_competition_hash UNIQUE (competition_id, normalized_url_hash);

    SET @giut_v24_drop_sql = CONCAT('ALTER TABLE competition_urls DROP INDEX `', REPLACE(legacy_name, '`', '``'), '`');
    PREPARE giut_v24_stmt FROM @giut_v24_drop_sql;
    EXECUTE giut_v24_stmt;
    DEALLOCATE PREPARE giut_v24_stmt;
END //
DELIMITER ;
CALL giut_migrate_official_url_uniqueness_v24();
DROP PROCEDURE giut_migrate_official_url_uniqueness_v24;
