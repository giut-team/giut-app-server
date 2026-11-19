-- 수동 적용용(MySQL 8.4 기준). 대상 DB·백업을 확인하고 **서버 배포 전에** 적용한다. 자동 실행하지 않는다.
-- 공모전 스크랩 순 정렬(GET /api/competitions?sort=SCRAPS)을 위해 competition_scraps 행 수를 competitions.scrap_count에 둔다.
-- 배포 뒤에는 스크랩 추가·취소 트랜잭션이 이 값을 ±1 한다. 기존 데이터는 아래 백필로 맞춘다.
--
-- 순서가 중요하다: 배포 전에 1)→3)을 적용한다. 배포 전 옛 서버는 이 컬럼을 모르므로, 1)~3) 사이와 배포 사이에
-- 들어온 스크랩·취소는 4)를 배포 직후 한 번 더 실행해 맞춘다(같은 UPDATE라 여러 번 실행해도 안전하다).

-- 1) 컬럼 추가(기존 행은 0).
ALTER TABLE competitions ADD COLUMN scrap_count BIGINT NOT NULL DEFAULT 0;

-- 2) 백필: 공모전별 실제 스크랩 행 수.
UPDATE competitions c
LEFT JOIN (SELECT competition_id, COUNT(*) AS cnt FROM competition_scraps GROUP BY competition_id) s
  ON s.competition_id = c.id
SET c.scrap_count = COALESCE(s.cnt, 0);

-- 3) 스크랩 순 정렬 인덱스(publication_status = ? ORDER BY scrap_count DESC, id DESC). InnoDB 보조 인덱스 끝에 PK가 붙는다.
ALTER TABLE competitions ADD INDEX idx_competitions_status_scrap_count (publication_status, scrap_count);

-- 4) 배포 직후 재정렬(필요 시 언제든 실행 가능): 2)와 같은 UPDATE.
-- UPDATE competitions c
-- LEFT JOIN (SELECT competition_id, COUNT(*) AS cnt FROM competition_scraps GROUP BY competition_id) s
--   ON s.competition_id = c.id
-- SET c.scrap_count = COALESCE(s.cnt, 0);

-- 확인: 0이어야 한다.
-- SELECT COUNT(*) FROM competitions c
-- WHERE c.scrap_count <> (SELECT COUNT(*) FROM competition_scraps s WHERE s.competition_id = c.id);

-- 복구: 이전 서버로 되돌려도 컬럼·인덱스는 사용되지 않을 뿐 영향이 없다. DROP은 별도 승인 없이 하지 않는다.
