-- 수동 적용용(MySQL 8.4 기준, SKIP LOCKED는 8.0.1+). 대상 DB·백업을 확인하고 서버 배포 전에 적용한다. 자동 실행하지 않는다.
-- (ddl-auto=update에서도 새 테이블은 생성되지만, 기존 notifications 컬럼 형식 변경과 인덱스는 이 파일로만 반영된다.)
-- 앱 내 알림함 + Expo 푸시 발송. 기존 users 데이터는 변경하지 않는다.

-- 1) 앱 내 알림. 이전 ddl-auto=update로 이미 만들어졌을 수 있다.
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    action_path TEXT NULL,
    payload JSON NULL,
    dedup_key VARCHAR(200) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_dedup_key UNIQUE (dedup_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    KEY idx_notifications_user_id (user_id, id)
);

-- 이미 있던 테이블이면 Hibernate가 notification_type을 MySQL ENUM(이전 유형 목록)으로 만들었을 수 있다.
-- ENUM이면 새 유형(COMPETITION_EXTRACTION_*) 저장이 실패하므로 VARCHAR로 바꾼다. 이미 VARCHAR여도 안전하다.
ALTER TABLE notifications MODIFY COLUMN notification_type VARCHAR(40) NOT NULL;

-- 최신순 커서 목록(user_id = ? AND id < ? ORDER BY id DESC)용 인덱스. 위 CREATE로 만든 경우 이미 있다.
-- 기존 테이블에만 실행한다: SHOW INDEX FROM notifications WHERE Key_name = 'idx_notifications_user_id'; 가 비어 있을 때
-- ALTER TABLE notifications ADD INDEX idx_notifications_user_id (user_id, id);

-- 2) 푸시를 받을 앱 설치(Expo 푸시 토큰). 로그아웃·토큰 무효는 삭제하지 않고 active=false로 둔다.
CREATE TABLE IF NOT EXISTS push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    installation_id VARCHAR(100) NOT NULL,
    push_token VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(40) NULL,
    active BIT(1) NOT NULL,
    deactivation_reason VARCHAR(40) NULL,
    last_seen_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_devices_installation UNIQUE (installation_id),
    CONSTRAINT fk_push_devices_user FOREIGN KEY (user_id) REFERENCES users (id),
    KEY idx_push_devices_user_active (user_id, active),
    KEY idx_push_devices_token (push_token)
);

-- 3) 알림 1건 × 기기 1대 발송(outbox). 알림을 만든 트랜잭션에서 PENDING으로 함께 저장된다.
CREATE TABLE IF NOT EXISTS push_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at DATETIME(6) NULL,
    lease_token VARCHAR(36) NULL,
    lease_expires_at DATETIME(6) NULL,
    ticket_id VARCHAR(100) NULL,
    sent_at DATETIME(6) NULL,
    receipt_check_at DATETIME(6) NULL,
    error_code VARCHAR(40) NULL,
    error_message VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_deliveries_notification_device UNIQUE (notification_id, device_id),
    CONSTRAINT fk_push_deliveries_notification FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_push_deliveries_device FOREIGN KEY (device_id) REFERENCES push_devices (id) ON DELETE CASCADE,
    -- 선점 후보 조회(잠금 없는 SELECT id ... WHERE status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at, id LIMIT ?).
    -- 잠금은 후보마다 PK 단건 SELECT ... FOR UPDATE SKIP LOCKED로 따로 건다(추출 작업과 같은 이유).
    KEY idx_push_deliveries_status_next (status, next_attempt_at),
    KEY idx_push_deliveries_status_lease (status, lease_expires_at),
    KEY idx_push_deliveries_status_receipt (status, receipt_check_at),
    KEY idx_push_deliveries_completed (completed_at)
);
-- 복구: 이전 서버로 되돌려도 새 테이블은 사용되지 않을 뿐 영향이 없다. DROP은 별도 승인 없이 하지 않는다.
