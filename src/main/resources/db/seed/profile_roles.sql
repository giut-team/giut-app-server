-- 대표 역할 및 세부 역할 마스터 데이터
-- 여러 번 실행해도 code 기준으로 최신 이름과 표시 순서만 갱신됩니다.

CREATE TABLE IF NOT EXISTS profile_role_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_role_categories_code (code),
    CONSTRAINT chk_profile_role_categories_code
        CHECK (code IN ('DEVELOPMENT', 'DESIGN', 'PLANNING', 'MARKETING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS profile_roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    primary_role_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_roles_code (code),
    CONSTRAINT fk_profile_roles_primary_role
        FOREIGN KEY (primary_role_id) REFERENCES profile_role_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO profile_role_categories (code, name, display_order) VALUES
    ('DEVELOPMENT', '개발', 1),
    ('DESIGN', '디자인', 2),
    ('PLANNING', '기획', 3),
    ('MARKETING', '마케팅', 4)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    display_order = VALUES(display_order);

INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'BACKEND_DEVELOPER', '백엔드 개발자', 1 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'FRONTEND_DEVELOPER', '프론트엔드 개발자', 2 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'MOBILE_DEVELOPER', '모바일 개발자', 3 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'DATA_ANALYST', '데이터 분석', 4 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'DATA_ENGINEER', '데이터 엔지니어', 5 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'AI_ENGINEER', 'AI 엔지니어', 6 FROM profile_role_categories WHERE code = 'DEVELOPMENT'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);

INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'UI_UX_DESIGNER', 'UI/UX 디자이너', 1 FROM profile_role_categories WHERE code = 'DESIGN'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'GRAPHIC_DESIGNER', '그래픽 디자이너', 2 FROM profile_role_categories WHERE code = 'DESIGN'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'PRODUCT_DESIGNER', '프로덕트 디자이너', 3 FROM profile_role_categories WHERE code = 'DESIGN'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);

INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'SERVICE_PLANNER', '서비스 기획', 1 FROM profile_role_categories WHERE code = 'PLANNING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'BUSINESS_PLANNER', '사업 기획', 2 FROM profile_role_categories WHERE code = 'PLANNING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'PROJECT_MANAGER', '프로젝트 매니저', 3 FROM profile_role_categories WHERE code = 'PLANNING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);

INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'DIGITAL_MARKETER', '디지털 마케터', 1 FROM profile_role_categories WHERE code = 'MARKETING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'CONTENT_MARKETER', '콘텐츠 마케터', 2 FROM profile_role_categories WHERE code = 'MARKETING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
INSERT INTO profile_roles (primary_role_id, code, name, display_order)
SELECT id, 'BRAND_MARKETER', '브랜드 마케터', 3 FROM profile_role_categories WHERE code = 'MARKETING'
ON DUPLICATE KEY UPDATE primary_role_id = VALUES(primary_role_id), name = VALUES(name), display_order = VALUES(display_order);
