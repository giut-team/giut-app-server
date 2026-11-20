-- MySQL 수동 적용용. 팀 초대 토큰은 SHA-256 해시로만 저장한다.
CREATE TABLE IF NOT EXISTS team_invite_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_team_invite_links_token_hash UNIQUE (token_hash),
    INDEX idx_team_invite_links_team_expires (team_id, expires_at)
);
