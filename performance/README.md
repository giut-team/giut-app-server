# GIUT 성능 기준 측정

이 디렉터리는 팀·채팅 API의 최적화 전후 성능을 같은 조건에서 비교하기 위한 자료를 저장합니다.

## 1. 전용 데이터베이스 준비

운영 DB나 평소 사용하는 개발 DB가 아닌 별도 DB를 사용합니다.

```sql
CREATE DATABASE giut_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 2. 데이터 생성

`perf` 프로필과 세 가지 안전 옵션을 모두 지정해야 데이터가 생성됩니다.

```bash
SPRING_PROFILES_ACTIVE=perf \
DB_URL='jdbc:mysql://localhost:3306/giut_perf?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME='root' \
DB_PASSWORD='로컬_MySQL_비밀번호' \
JWT_SECRET='로컬_JWT_SECRET' \
PERF_SEED_ENABLED=true \
PERF_SEED_CONFIRMATION='CREATE_PERFORMANCE_DATA' \
PERF_SEED_PASSWORD='성능측정계정_비밀번호' \
./gradlew bootRun
```

기본 생성 규모는 다음과 같습니다.

| 데이터 | 기본값 |
|---|---:|
| 팀 | 1,000개 |
| 팀별 활성 팀원 | 5명 |
| 팀별 대기 지원 | 20개 |
| 측정 사용자 개인 채팅방 | 100개 |
| 채팅방별 메시지 | 1,000개 |
| 전체 메시지 | 100,000개 |

측정용 관리자 계정 이메일은 `perf-main@giut.local`이고, 비밀번호는 실행할 때 입력한
`PERF_SEED_PASSWORD` 값입니다. 생성 완료 로그에 측정 사용자 ID와 공모전 ID가 출력됩니다.

## 3. 소규모 동작 확인

먼저 빠르게 생성 동작만 확인할 때는 규모를 줄일 수 있습니다.

```bash
PERF_TEAM_COUNT=10 \
PERF_TEAM_MEMBERS_PER_TEAM=3 \
PERF_APPLICATIONS_PER_TEAM=2 \
PERF_CHAT_ROOM_COUNT=5 \
PERF_MESSAGES_PER_ROOM=20 \
PERF_SEED_ENABLED=true \
PERF_SEED_CONFIRMATION='CREATE_PERFORMANCE_DATA' \
PERF_SEED_PASSWORD='성능측정계정_비밀번호' \
SPRING_PROFILES_ACTIVE=perf \
DB_URL='jdbc:mysql://localhost:3306/giut_perf?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME='root' \
DB_PASSWORD='로컬_MySQL_비밀번호' \
JWT_SECRET='로컬_JWT_SECRET' \
./gradlew bootRun
```

## 4. 재생성

측정 사용자가 이미 존재하면 중복 생성을 건너뜁니다. 동일한 조건으로 다시 생성하려면
전용 `giut_perf` DB를 삭제 후 다시 생성합니다. 운영 및 일반 개발 DB에는 실행하지 않습니다.

## 환경변수로 규모 변경

| 환경변수 | 설명 |
|---|---|
| `PERF_TEAM_COUNT` | 생성할 팀 수 |
| `PERF_TEAM_MEMBERS_PER_TEAM` | 팀장 포함 팀별 활성 팀원 수 |
| `PERF_APPLICATIONS_PER_TEAM` | 팀별 PENDING 지원 수 |
| `PERF_CHAT_ROOM_COUNT` | 측정 사용자의 개인 채팅방 수 |
| `PERF_MESSAGES_PER_ROOM` | 채팅방별 메시지 수 |
| `PERF_SEED_BATCH_SIZE` | flush/clear 단위 |

## SQL 실행 횟수 기준 측정

Hibernate Statistics를 이용해 핵심 팀·채팅 조회의 SQL 문장 수를 자동 측정합니다.

```bash
./gradlew test \
  --tests com.giut.server.performance.PerformanceQueryCountBaselineTest
```

결과는 다음 파일에 생성됩니다.

```text
build/reports/performance/sql-query-baseline.md
```

이 테스트의 처리 시간은 H2 기반 개발용 참고값입니다. 실제 평균·p95·처리량은 별도의
MySQL 성능 DB를 대상으로 k6에서 측정합니다.

## k6 로컬 HTTP 기준 측정

Spring Boot를 임시 HTTP 포트와 H2 DB로 실행한 뒤 k6가 외부 클라이언트처럼 핵심 API를
호출합니다. 평소 테스트에서는 실행되지 않으며 `RUN_K6=true`일 때만 동작합니다.

```bash
RUN_K6=true ./gradlew test \
  --tests com.giut.server.performance.K6RestBaselineIntegrationTest
```

측정 조건은 가상 사용자 10명, 5초 증가, 15초 고정 부하, 2초 감소이며 API별로 각각
실행합니다. 결과는 다음 디렉터리에 생성됩니다.

```text
build/reports/performance/k6/
├── team-list-baseline.md
├── team-detail-baseline.md
├── chat-room-list-baseline.md
├── message-list-baseline.md
└── *-k6.log
```

로컬 H2 결과는 개선 전후 개발 기준 비교에 사용합니다. 최종 포트폴리오 수치는 동일한
`performance/k6/rest-baseline.js`를 MySQL 성능 DB와 고정된 서버 사양에서 다시 실행해
기록합니다.
