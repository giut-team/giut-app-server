-- 수동 적용용. 대상 DB 백업과 아래 중복 검사를 확인한 뒤 신규 서버 배포 전에 적용한다.
-- 동일한 두 사용자의 개인 채팅방이 둘 이상이면 UPDATE가 유니크 제약에 실패한다.
-- 실패 시 임의의 방을 삭제하지 말고 메시지 이력을 어떤 방으로 합칠지 먼저 결정한다.
--
-- 적용 전 중복 검사:
-- SELECT participant_pair, COUNT(*) AS room_count
-- FROM (
--     SELECT room.id,
--            CONCAT(MIN(member.user_id), ':', MAX(member.user_id)) AS participant_pair
--     FROM chat_rooms room
--     JOIN chat_room_members member ON member.chat_room_id = room.id
--     WHERE room.type = 'PERSONAL'
--     GROUP BY room.id
--     HAVING COUNT(DISTINCT member.user_id) = 2
-- ) personal_rooms
-- GROUP BY participant_pair
-- HAVING COUNT(*) > 1;

UPDATE chat_rooms
SET personal_chat_key = (
    SELECT CONCAT(MIN(member.user_id), ':', MAX(member.user_id))
    FROM chat_room_members member
    WHERE member.chat_room_id = chat_rooms.id
)
WHERE type = 'PERSONAL'
  AND personal_chat_key IS NULL
  AND 2 = (
      SELECT COUNT(DISTINCT member.user_id)
      FROM chat_room_members member
      WHERE member.chat_room_id = chat_rooms.id
  );

-- 적용 후 NULL이 남아 있다면 참여자가 정확히 두 명이 아닌 비정상 개인 채팅방이므로 별도 확인한다.
-- SELECT id FROM chat_rooms WHERE type = 'PERSONAL' AND personal_chat_key IS NULL;
