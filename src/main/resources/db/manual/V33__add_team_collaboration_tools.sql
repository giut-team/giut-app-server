-- MySQL 수동 적용용. 온라인 팀 생성·수정에 필요한 변경이므로 새 서버를 기동하기 전에 실행한다.
ALTER TABLE teams
    MODIFY COLUMN meeting_place ENUM('ANYWHERE', 'CAMPUS', 'METROPOLITAN_AREA', 'SEOUL') NULL;

CREATE TABLE IF NOT EXISTS team_collaboration_tools (
    team_id BIGINT NOT NULL,
    tool_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (team_id, tool_code),
    CONSTRAINT fk_team_collaboration_tools_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

-- 초기 V33으로 ENUM 컬럼이 이미 생성된 DB에서만 실행한다.
-- ALTER TABLE team_collaboration_tools MODIFY COLUMN tool_code VARCHAR(30) NOT NULL;
