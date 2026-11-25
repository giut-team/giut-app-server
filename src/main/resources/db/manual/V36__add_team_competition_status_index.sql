-- MySQL 8.4 수동 적용용. 대상 스키마를 선택한 뒤 실행한다.
-- 모집 목록과 COUNT가 공모전 및 모집 상태로 인덱스를 탐색하도록 한다.
-- InnoDB 보조 인덱스 끝의 PK(id)를 역순으로 읽어 목록의 id DESC 정렬을 지원한다.
-- ddl-auto=update로 이미 생성된 환경에서도 다시 실행할 수 있다.
SET @team_index_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'teams'
          AND index_name = 'idx_teams_competition_status'
    ),
    'SELECT ''idx_teams_competition_status already exists'' AS result',
    'ALTER TABLE teams ADD INDEX idx_teams_competition_status (competition_id, status)'
);
PREPARE team_index_statement FROM @team_index_ddl;
EXECUTE team_index_statement;
DEALLOCATE PREPARE team_index_statement;
SET @team_index_ddl = NULL;
