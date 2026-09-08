-- 대표 역할은 개발, 디자인, 기획, 마케팅 네 가지 코드만 허용합니다.
ALTER TABLE profile_role_categories
    ADD CONSTRAINT chk_profile_role_categories_code
    CHECK (code IN ('DEVELOPMENT', 'DESIGN', 'PLANNING', 'MARKETING'));
