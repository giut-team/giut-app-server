-- 기존 user_profiles 및 user_profile_roles 데이터가 없을 때 한 번만 실행하는 전환 SQL입니다.
-- 실행 전 반드시 두 테이블의 데이터 수를 확인합니다.

DROP TABLE user_profile_roles;

ALTER TABLE user_profiles DROP COLUMN primary_role;
ALTER TABLE user_profiles ADD COLUMN primary_role_id BIGINT NOT NULL;
ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_primary_role
    FOREIGN KEY (primary_role_id) REFERENCES profile_role_categories (id);

CREATE TABLE user_profile_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_profile_roles_role
        FOREIGN KEY (role_id) REFERENCES profile_roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
