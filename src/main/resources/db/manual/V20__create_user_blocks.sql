-- 수동 적용용. 대상 DB/백업을 확인하고 서버 배포 전에 적용한다. 자동 실행하지 않는다.
-- 기존 추천/프로필/채팅/팀 데이터는 변경하지 않는다.
CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_user_id, blocked_user_id),
    KEY idx_user_blocks_target (blocked_user_id, blocker_user_id),
    KEY idx_user_blocks_owner_created (blocker_user_id, created_at, id),
    CONSTRAINT fk_user_blocks_owner FOREIGN KEY (blocker_user_id) REFERENCES users(id),
    CONSTRAINT fk_user_blocks_target FOREIGN KEY (blocked_user_id) REFERENCES users(id),
    CONSTRAINT chk_user_blocks_distinct CHECK (blocker_user_id <> blocked_user_id)
);
-- 복구: 이전 서버로 되돌리되 차단 테이블 데이터는 보존한다. DROP은 별도 승인 없이 하지 않는다.
