-- 수동 적용용: 서버/프론트 배포 전 DBA가 대상 DB를 확인한 뒤 적용한다.
-- 새 빈 테이블만 추가하며 기존 데이터는 변경하지 않는다. 여기서는 자동 실행하지 않는다.
CREATE TABLE IF NOT EXISTS profile_scraps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_profile_scraps_user_profile UNIQUE (user_id, profile_user_id),
    KEY idx_profile_scraps_profile (profile_user_id),
    CONSTRAINT fk_profile_scraps_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_profile_scraps_profile FOREIGN KEY (profile_user_id) REFERENCES user_profiles(user_id)
);
-- 복구는 먼저 해당 기능을 사용하지 않는 서버/프론트로 되돌린다.
-- 데이터 보존이 필요하므로 테이블 삭제는 자동 수행하지 않는다.
