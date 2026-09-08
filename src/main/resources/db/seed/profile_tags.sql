-- Flyway를 사용하지 않는 현재 환경에서는 아래 파일을 MySQL에 한 번 실행합니다.
-- INSERT IGNORE를 사용하므로 여러 번 실행해도 기존 태그를 중복 생성하지 않습니다.

INSERT IGNORE INTO profile_tags (tag_type, name, normalized_name, created_at) VALUES
    ('SKILL', 'Python', 'python', '2026-09-08 00:00:00'),
    ('SKILL', 'SQL', 'sql', '2026-09-08 00:00:00'),
    ('SKILL', 'Pandas', 'pandas', '2026-09-08 00:00:00'),
    ('SKILL', 'React', 'react', '2026-09-08 00:00:00'),
    ('SKILL', 'TypeScript', 'typescript', '2026-09-08 00:00:00'),
    ('SKILL', 'Tableau', 'tableau', '2026-09-08 00:00:00'),
    ('SKILL', 'Excel · 함수', 'excel · 함수', '2026-09-08 00:00:00'),
    ('SKILL', 'Java', 'java', '2026-09-08 00:00:00'),
    ('SKILL', 'R', 'r', '2026-09-08 00:00:00'),
    ('SKILL', 'Node.js', 'node.js', '2026-09-08 00:00:00'),
    ('SKILL', 'Spring', 'spring', '2026-09-08 00:00:00'),
    ('SKILL', 'Swift', 'swift', '2026-09-08 00:00:00'),
    ('SKILL', 'Kotlin', 'kotlin', '2026-09-08 00:00:00'),
    ('SKILL', 'Notion', 'notion', '2026-09-08 00:00:00'),
    ('SKILL', 'PPT 기획서', 'ppt 기획서', '2026-09-08 00:00:00'),
    ('SKILL', '설문·인터뷰', '설문·인터뷰', '2026-09-08 00:00:00'),
    ('SKILL', 'GA4', 'ga4', '2026-09-08 00:00:00'),
    ('SKILL', '카피라이팅', '카피라이팅', '2026-09-08 00:00:00'),
    ('SKILL', 'Figma', 'figma', '2026-09-08 00:00:00'),
    ('SKILL', 'Illustrator', 'illustrator', '2026-09-08 00:00:00'),
    ('SKILL', 'Premiere', 'premiere', '2026-09-08 00:00:00'),
    ('SKILL', 'SNS 운영', 'sns 운영', '2026-09-08 00:00:00');

INSERT IGNORE INTO profile_tags (tag_type, name, normalized_name, created_at) VALUES
    ('INTEREST', 'IT/과학', 'it/과학', '2026-09-08 00:00:00'),
    ('INTEREST', '데이터', '데이터', '2026-09-08 00:00:00'),
    ('INTEREST', '창업', '창업', '2026-09-08 00:00:00'),
    ('INTEREST', '환경/ESG', '환경/esg', '2026-09-08 00:00:00'),
    ('INTEREST', '문화/예술', '문화/예술', '2026-09-08 00:00:00');

INSERT IGNORE INTO profile_tags (tag_type, name, normalized_name, created_at) VALUES
    ('EXPERIENCE', '공모전', '공모전', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '해커톤', '해커톤', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '팀 프로젝트', '팀 프로젝트', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '창업', '창업', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '대외활동', '대외활동', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '인턴십', '인턴십', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '동아리', '동아리', '2026-09-08 00:00:00'),
    ('EXPERIENCE', '수상', '수상', '2026-09-08 00:00:00');
