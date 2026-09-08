-- 대표 역할은 코드가 고정되어 있으므로 Enum으로 관리하고,
-- 세부 역할만 profile_roles 테이블에서 관리합니다.

CREATE TABLE IF NOT EXISTS profile_roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    primary_role VARCHAR(30) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_roles_code (code),
    CONSTRAINT chk_profile_roles_primary_role
        CHECK (primary_role IN ('DEVELOPMENT', 'DESIGN', 'PLANNING', 'MARKETING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO profile_roles (primary_role, code, name, display_order) VALUES
    ('DEVELOPMENT', 'BACKEND_DEVELOPER', '백엔드 개발자', 1),
    ('DEVELOPMENT', 'FRONTEND_DEVELOPER', '프론트엔드 개발자', 2),
    ('DEVELOPMENT', 'MOBILE_DEVELOPER', '모바일 개발자', 3),
    ('DEVELOPMENT', 'DATA_ANALYST', '데이터 분석', 4),
    ('DEVELOPMENT', 'DATA_ENGINEER', '데이터 엔지니어', 5),
    ('DEVELOPMENT', 'AI_ENGINEER', 'AI 엔지니어', 6),
    ('DESIGN', 'UI_UX_DESIGNER', 'UI/UX 디자이너', 1),
    ('DESIGN', 'GRAPHIC_DESIGNER', '그래픽 디자이너', 2),
    ('DESIGN', 'PRODUCT_DESIGNER', '프로덕트 디자이너', 3),
    ('PLANNING', 'SERVICE_PLANNER', '서비스 기획', 1),
    ('PLANNING', 'BUSINESS_PLANNER', '사업 기획', 2),
    ('PLANNING', 'PROJECT_MANAGER', '프로젝트 매니저', 3),
    ('MARKETING', 'DIGITAL_MARKETER', '디지털 마케터', 1),
    ('MARKETING', 'CONTENT_MARKETER', '콘텐츠 마케터', 2),
    ('MARKETING', 'BRAND_MARKETER', '브랜드 마케터', 3)
ON DUPLICATE KEY UPDATE
    primary_role = VALUES(primary_role),
    name = VALUES(name),
    display_order = VALUES(display_order);
