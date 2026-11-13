-- 수동 적용용(MySQL 8.4 기준, SKIP LOCKED는 8.0.1+). 대상 DB·백업을 확인하고 서버 배포 전에 적용한다. 자동 실행하지 않는다.
-- (현재 설정 ddl-auto=update에서도 같은 구조가 생성되지만, 이 파일을 운영 구조의 기준으로 명시 적용한다.)
-- 회원 공모전 URL 비동기 AI 추출 작업. 새 테이블만 추가하며 기존 users/competitions/competition_urls 데이터는 변경하지 않는다.
-- 같은 URL의 작업은 회원 수와 무관하게 하나만 실행한다. 작업을 볼 수 있는 회원은 아래 구독 테이블로 관리한다.
CREATE TABLE IF NOT EXISTS competition_extraction_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    source_url_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_key VARCHAR(90) NULL,
    attempt_count INT NOT NULL,
    next_attempt_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    lease_token VARCHAR(36) NULL,
    result_json MEDIUMTEXT NULL,
    error_code VARCHAR(40) NULL,
    error_message VARCHAR(300) NULL,
    internal_error_detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- PENDING/RUNNING 동안만 값(URL hash)이 있다. 같은 URL의 진행 중 작업을 사용자와 무관하게 하나로 제한한다.
    -- UNIQUE는 NULL을 여러 개 허용하므로 종료 작업은 제한하지 않는다.
    CONSTRAINT uk_extraction_jobs_active_key UNIQUE (active_key),
    CONSTRAINT fk_extraction_jobs_requester FOREIGN KEY (requester_user_id) REFERENCES users (id),
    -- 선점 후보 조회(잠금 없는 일반 SELECT: status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at, id LIMIT ?)가 이 인덱스로 읽는다.
    -- 잠금은 후보마다 PK 단건 SELECT ... FOR UPDATE SKIP LOCKED로 따로 건다(한 단계 SKIP LOCKED 조회는 읽은 행을 모두 잠가 폐기).
    KEY idx_extraction_jobs_status_next (status, next_attempt_at),
    KEY idx_extraction_jobs_status_lease (status, lease_expires_at),
    KEY idx_extraction_jobs_requester_created (requester_user_id, created_at),
    KEY idx_extraction_jobs_hash_status (source_url_hash, status),
    KEY idx_extraction_jobs_expires (expires_at)
);

-- 작업을 조회할 수 있는 회원(처음 접수한 회원 + 같은 URL로 합류한 회원). 작업이 지워지면 함께 지워진다.
CREATE TABLE IF NOT EXISTS competition_extraction_job_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_extraction_subscriptions_job_user UNIQUE (job_id, user_id),
    CONSTRAINT fk_extraction_subscriptions_job FOREIGN KEY (job_id) REFERENCES competition_extraction_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_extraction_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    KEY idx_extraction_subscriptions_user (user_id, job_id)
);
-- 복구: 이전 서버로 되돌려도 이 테이블은 사용되지 않을 뿐 영향이 없다. DROP은 별도 승인 없이 하지 않는다.
