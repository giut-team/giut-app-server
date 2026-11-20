-- MySQL 수동 적용용. 기존 지원서와 팀 합류 제안의 시간 정보는 NULL로 유지한다.
ALTER TABLE team_applications
    ADD COLUMN weekly_available_hours INT NULL,
    ADD COLUMN meeting_time_slots JSON NULL;
