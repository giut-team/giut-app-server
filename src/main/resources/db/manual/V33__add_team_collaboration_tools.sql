-- MySQL 수동 적용용. 온라인 팀의 회의 장소 생략 및 팀별 협업 도구 저장을 지원한다.
ALTER TABLE teams
    MODIFY COLUMN meeting_place ENUM('ANYWHERE', 'CAMPUS', 'METROPOLITAN_AREA', 'SEOUL') NULL;

CREATE TABLE IF NOT EXISTS team_collaboration_tools (
    team_id BIGINT NOT NULL,
    tool_code ENUM('DISCORD', 'GOOGLE_MEET', 'NOTION', 'SLACK', 'ZOOM') NOT NULL,
    PRIMARY KEY (team_id, tool_code),
    CONSTRAINT fk_team_collaboration_tools_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
