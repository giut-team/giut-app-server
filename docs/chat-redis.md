# 다중 서버 채팅 Redis 설정

같은 환경의 앱 서버에 `CHAT_REDIS_ENABLED=true`를 설정하고, 동일한 `SPRING_DATA_REDIS_URL`과 `CHAT_REDIS_CHANNEL`을 지정합니다. 채널명은 필수입니다. 개발과 운영은 각각 `giut:chat:dev:messages:v1`, `giut:chat:prod:messages:v1`처럼 서로 다른 채널을 사용합니다. Redis 데이터베이스 번호는 Pub/Sub 채널을 분리하지 않습니다.

구독 서버는 Redis 페이로드가 신뢰할 수 있는 앱 서버에서 발행됐다고 전제합니다. Redis는 앱 서버만 접근할 수 있는 사설 네트워크에 두고, 운영에서는 TLS(`rediss://`)와 인증을 사용합니다. Redis ACL을 설정할 수 있다면 앱 계정의 채널 권한을 해당 환경의 채널로 제한합니다.

MySQL 커밋 후 요청을 받은 서버가 로컬 STOMP로 전달합니다. Redis 발행은 별도 실행기에서 처리하며, 다른 서버는 Redis 이벤트를 받아 전달합니다. 발행 서버는 자기 이벤트를 무시합니다. Redis 발행 실패나 대기열 포화 시 로그를 남깁니다. 이 경우 MySQL 저장과 같은 서버의 전달은 유지되며, 다른 서버의 누락 이벤트는 Redis에서 재생되지 않습니다.
