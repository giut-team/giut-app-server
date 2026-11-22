-- 수동 적용용(MySQL 8.4 기준). 대상 DB·백업을 확인하고 적용한다. 자동 실행하지 않는다.
-- 공개 공모전 목록의 마감 임박 순·카테고리+마감 임박 순·조회수 순이 매번 공개 공모전 전체를 정렬(filesort)하지 않도록
-- 정렬 순서대로 읽을 수 있는 인덱스를 추가한다. 배포 전후 어느 쪽에 적용해도 된다(코드 변경은 엔티티 인덱스 정의뿐이다).
-- 근거: performance/results/mysql-competition-list-sort-index-2026-11-22.md

-- 1) 마감 임박 순(ORDER BY application_end_at, id DESC)과 마감 임박 목록(/closing-soon).
--    정렬 방향이 섞여 있어 id DESC까지 인덱스에 둔다(MySQL 8.0+ 내림차순 인덱스).
ALTER TABLE competitions ADD INDEX idx_competitions_status_app_end_at_id
    (publication_status, application_end_at, id DESC);

-- 2) 카테고리 + 마감 임박 순.
ALTER TABLE competitions ADD INDEX idx_competitions_status_category_app_end_at_id
    (publication_status, category, application_end_at, id DESC);

-- 3) 조회수 순(ORDER BY view_count DESC, id DESC). InnoDB 보조 인덱스 끝에 PK가 붙어 역순으로 읽으면 id DESC가 된다.
--    상세 조회마다 view_count를 올리므로 이 인덱스도 그때마다 갱신된다.
ALTER TABLE competitions ADD INDEX idx_competitions_status_view_count (publication_status, view_count);

-- 확인: 아래 실행 계획에 "Sort:"가 없고 위 인덱스 이름이 나와야 한다.
-- EXPLAIN SELECT id FROM competitions WHERE publication_status = 'PUBLISHED' AND application_end_at > NOW(6)
--   ORDER BY application_end_at, id DESC LIMIT 11;
-- EXPLAIN SELECT id FROM competitions WHERE publication_status = 'PUBLISHED' AND application_end_at > NOW(6)
--   ORDER BY view_count DESC, id DESC LIMIT 11;

-- 4) 1)을 적용하면 예전 인덱스 idx_competitions_status_app_end_at(publication_status, application_end_at)는
--    1)의 앞부분과 같아 쓰이지 않는다. 1)~3) 적용과 배포가 끝나고 실행 계획을 확인한 뒤 따로 지운다.
-- ALTER TABLE competitions DROP INDEX idx_competitions_status_app_end_at;

-- 복구: 이전 서버로 되돌려도 인덱스는 사용되지 않을 뿐 영향이 없다. DROP은 별도 승인 없이 하지 않는다.
