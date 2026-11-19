-- k6 공모전 부하 테스트용 시드 (giut_k6 폐기용 DB 전용)
-- 사용자 5,000명(학교 인증 완료 STUDENT), 공모전 50,000건, 스크랩 약 55만 건(인기 공모전 쏠림)
SET SESSION cte_max_recursion_depth = 1000000;

DROP TABLE IF EXISTS k6_digit;
CREATE TABLE k6_digit (d INT PRIMARY KEY);
INSERT INTO k6_digit VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

DROP TABLE IF EXISTS k6_seq;
CREATE TABLE k6_seq (n INT PRIMARY KEY);
INSERT INTO k6_seq
SELECT a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000
FROM k6_digit a, k6_digit b, k6_digit c, k6_digit d, k6_digit e, k6_digit f;

-- 사용자: id는 AUTO_INCREMENT로 이어서 붙는다(기존 1~4 다음).
INSERT INTO users (created_at, updated_at, email, nickname, role, status, token_version, university_email, university_verified_at)
SELECT NOW(6), NOW(6),
       CONCAT('k6-user-', LPAD(n, 5, '0'), '@giut.local'),
       CONCAT('k6유저', n),
       'STUDENT', 'ACTIVE', 0,
       CONCAT('k6-', LPAD(n, 5, '0'), '@uos.ac.kr'),
       NOW(6)
FROM k6_seq WHERE n < 5000;

-- 공모전 50,000건
--  · 90%: PUBLISHED + 마감 1시간~120일 뒤 (약 6%가 7일 이내 → 마감 임박 목록에 걸림)
--  · 5%: DRAFT, 5%: PUBLISHED지만 이미 마감
INSERT INTO competitions (created_at, updated_at, activity_type, application_start_at, application_end_at, category,
                          host_organization, is_for_enrolled_only, is_team, prize_info, publication_status, scrap_count,
                          summary, target_participants, title, total_prize_amount, verification_status, verified_at, view_count)
SELECT NOW(6) - INTERVAL (50000 - n) MINUTE,
       NOW(6) - INTERVAL (50000 - n) MINUTE,
       ELT(1 + n % 5, 'COMPETITION', 'CONTEST', 'HACKATHON', 'SUPPORTERS', 'STARTUP'),
       NOW(6) - INTERVAL 10 DAY,
       CASE WHEN n % 20 = 1 THEN NOW(6) - INTERVAL (1 + n % 30) DAY
            ELSE NOW(6) + INTERVAL (1 + (n * 7919) % (120 * 24)) HOUR END,
       ELT(1 + n % 14, 'ADVERTISING_MARKETING','AI_DATA','ARCHITECTURE_CONSTRUCTION_INTERIOR','DESIGN_CHARACTER_WEBTOON',
           'ENTERTAINMENT_ART_MUSIC','ETC','GAME_SOFTWARE','LITERATURE_WRITING_SCENARIO','NAMING_SLOGAN','PAPER_REPORT',
           'PLANNING_IDEA','SCIENCE_ENGINEERING','VIDEO_UCC_PHOTO','WEB_MOBILE_IT'),
       CONCAT('주최기관 ', n % 500),
       b'0', b'1',
       CONCAT('총상금 ', (n % 50) * 10, '만원'),
       CASE WHEN n % 20 = 0 THEN 'DRAFT' ELSE 'PUBLISHED' END,
       0,
       CONCAT('k6 부하 테스트용 공모전 요약 ', n),
       '대학생',
       CONCAT(ELT(1 + n % 8, 'AI', '데이터', '디자인', '마케팅', '앱', '게임', '영상', '아이디어'), ' 공모전 ', n),
       (n % 50) * 100000,
       ELT(1 + n % 3, 'AUTO_VERIFIED', 'MANUALLY_VERIFIED', 'UNVERIFIED'),
       CASE WHEN n % 3 = 2 THEN NULL ELSE NOW(6) END,
       FLOOR(POW(RAND(n + 7), 4) * 20000)
FROM k6_seq WHERE n < 50000;

-- 스크랩: 사용자마다 낮은 id(=오래된) 쪽에 쏠린 분포로 공모전을 고른다. 중복(같은 사용자·공모전)은 IGNORE.
SET @first_user = (SELECT MIN(id) FROM users WHERE email LIKE 'k6-user-%');
SET @min_comp = (SELECT MIN(id) FROM competitions WHERE title NOT LIKE '[PERF]%');
INSERT IGNORE INTO competition_scraps (created_at, updated_at, user_id, competition_id)
SELECT NOW(6), NOW(6),
       @first_user + (n % 5000),
       @min_comp + FLOOR(POW(RAND(n * 31 + 3), 2.2) * 50000)
FROM k6_seq WHERE n < 640000;

UPDATE competitions c
LEFT JOIN (SELECT competition_id, COUNT(*) cnt FROM competition_scraps GROUP BY competition_id) s
       ON s.competition_id = c.id
SET c.scrap_count = COALESCE(s.cnt, 0);

DROP TABLE k6_seq;
DROP TABLE k6_digit;

ANALYZE TABLE users, competitions, competition_scraps;

SELECT (SELECT COUNT(*) FROM users) users,
       (SELECT COUNT(*) FROM competitions) competitions,
       (SELECT COUNT(*) FROM competitions WHERE publication_status='PUBLISHED' AND application_end_at > NOW()) open_published,
       (SELECT COUNT(*) FROM competitions WHERE publication_status='PUBLISHED' AND application_end_at BETWEEN NOW() AND NOW() + INTERVAL 7 DAY) closing_7d,
       (SELECT COUNT(*) FROM competition_scraps) scraps,
       (SELECT MAX(scrap_count) FROM competitions) max_scraps,
       (SELECT MIN(id) FROM users WHERE email LIKE 'k6-user-%') first_k6_user,
       (SELECT MAX(id) FROM users) last_user,
       (SELECT MIN(id) FROM competitions WHERE title NOT LIKE '[PERF]%') first_comp,
       (SELECT MAX(id) FROM competitions) last_comp;
