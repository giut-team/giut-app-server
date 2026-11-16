-- 수동 적용용. 대상 DB/백업을 확인하고 서버 배포 전에 적용한다. 자동 실행하지 않는다.
-- 기존 메시지는 client_message_id가 NULL인 상태로 유지한다.
ALTER TABLE chat_messages
    ADD COLUMN client_message_id VARCHAR(36) NULL AFTER sender_id;

ALTER TABLE chat_messages
    ADD CONSTRAINT uk_chat_messages_sender_client_message
        UNIQUE (sender_id, client_message_id);

-- 복구 시에는 신규 서버를 먼저 중단하고 유니크 제약과 컬럼 사용 여부를 확인한다.
