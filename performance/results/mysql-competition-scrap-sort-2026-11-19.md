# 공모전 스크랩 순 정렬: COUNT 서브쿼리 vs scrap_count 컬럼 (2026-11-19)

## 결론

`GET /api/competitions?sort=SCRAPS`의 서비스 호출 시간이 중앙값 **약 43.7ms → 약 12.5ms(약 71% 감소, 3.5배)**로 줄었다.
DB 쿼리만 보면 **약 32.5ms → 약 0.13ms**다. 스크랩 순 정렬이 이제 최근 등록 순(`LATEST`, 약 13ms)과 같은 수준이다.
스크랩 추가·취소는 중앙값 기준 1~2ms 안팎으로, 측정 간 흔들림보다 큰 차이는 확인되지 않았다.

## 환경과 데이터

- MySQL 26.7.0(Homebrew, 로컬 루프백 폐기 인스턴스, InnoDB buffer pool 512MB). 운영 RDS 8.4와 버전·하드웨어가 다르다.
- 같은 시드(`CompetitionSortMySqlBenchmarkTest`): 공모전 10,000건(공개·마감 전 9,036건), 사용자 2,000명, 스크랩 242,778건(소수 인기 공모전에 몰린 분포, 최대 2,000).
- 측정: `CompetitionService#getCompetitions`(응답 조립 포함) 워밍업 10회 후 50회, page size 20. 변경 전(`1d864b8`)과 변경 후를 번갈아 실행.
- 변경 전 4회, 변경 후 3회 실행의 중앙값을 아래에 적었다(괄호는 실행별 범위).

## 결과

| 요청 | 변경 전 중앙값(ms) | 변경 후 중앙값(ms) | 변화 |
|---|---:|---:|---:|
| `sort=SCRAPS` page=0 | 43.7 (43.0–44.9) | 12.5 (12.4–13.2) | −71% |
| `sort=SCRAPS` page=200 | 43.7 (42.5–47.5) | 12.1 (11.7–14.0) | −72% |
| `sort=SCRAPS` keyword=7 | 29.7 (29.5–34.9) | 14.7 (12.7–15.9) | −51% |
| SQL만(`EXPLAIN ANALYZE`, page=0) | 32.5 (32.2–33.4) | 0.13 (0.10–0.31) | 약 250배 |
| 스크랩 추가(서비스) | 1.8 (1.4–2.4) | 2.5 (1.9–4.1) | 흔들림 범위 안 |
| 스크랩 취소(서비스) | 1.7 (1.3–2.1) | 2.0 (1.6–3.5) | 흔들림 범위 안 |

참고: 정렬 코드가 바뀌지 않은 `LATEST`·`DEADLINE`·`VIEWS`도 실행마다 중앙값이 최대 ±30% 흔들렸다(예: `VIEWS` 19~28ms). 위 쓰기 비용 차이는 이 범위 안이라 개선·악화를 단정하지 않는다.

## 실행 계획

변경 전: 공개 공모전 9,036건마다 스크랩 수 서브쿼리를 실행(`loops=9036`)한 뒤 전부 정렬한다.

```
-> Limit: 20 row(s)  (actual time=33.4..33.4 rows=20 loops=1)
    -> Sort: (select #2) DESC, c.id DESC, limit input to 20 row(s) per chunk  (actual time=33.4..33.4 rows=20 loops=1)
        -> Filter: (c.application_end_at > <cache>(now(6)))  (actual time=0.0603..5.1 rows=9036 loops=1)
            -> Index lookup on c using idx_competitions_status_id (publication_status = 'PUBLISHED')  (rows=9036 loops=1)
-> Select #2 (subquery in projection; dependent)
    -> Aggregate: count(s.id)  (actual time=0.00297..0.00299 rows=1 loops=9036)
        -> Covering index lookup on s using fk_competition_scraps_competition (competition_id = c.id)  (rows=24.2 loops=9036)
```

변경 후: `(publication_status, scrap_count)` 인덱스를 역순으로 읽다가 조건에 맞는 20건에서 멈춘다.

```
-> Limit: 20 row(s)  (actual time=0.0922..0.096 rows=20 loops=1)
    -> Filter: ((c.publication_status = 'PUBLISHED') and (c.application_end_at > <cache>(now(6))))  (rows=20 loops=1)
        -> Index lookup on c using idx_competitions_status_scrap_count (publication_status = 'PUBLISHED') (reverse)  (rows=20 loops=1)
```

## 정합성·동시성

- 스크랩 행을 실제로 추가·삭제한 같은 트랜잭션에서 `scrap_count`를 원자적 UPDATE로 ±1 한다. 중복 스크랩·없는 스크랩 취소는 수를 바꾸지 않는다.
- 첫 구현은 서로 다른 회원 64명이 같은 공모전을 동시에 스크랩할 때 **MySQL 교착**이 났다(스크랩 INSERT의 외래 키 검사가 공모전 행에 공유 잠금 → 이어진 UPDATE가 배타 잠금으로 승격하며 서로 대기). 스크랩 추가·취소에서 공모전 행을 처음부터 `FOR UPDATE`로 읽도록 바꿔 해결했다. 잠금 순서는 회원 행 → 공모전 행.
- 수정 후 동시 스크랩 64건 → 64, 동시 취소 32건 + 중복 스크랩 32건 → 32로 카운터와 실제 행 수가 일치(3회 모두 통과).
- 대가: 같은 공모전에 대한 스크랩 변경은 공모전 행에서 한 건씩 처리된다(트랜잭션 수 ms). 한 공모전에 초당 수백 건이 몰리면 분산 카운터·비동기 집계를 검토한다.

## 재현

```bash
# 루프백 폐기 MySQL(127.0.0.1:33397, root/giut-local-test)을 띄운 뒤
GIUT_PERF_MYSQL_TEST=true GIUT_BENCH_OUT=/tmp/bench.md \
  ./gradlew test --rerun --tests 'com.giut.server.performance.CompetitionSortMySqlBenchmarkTest'
```

운영 적용 전 `src/main/resources/db/manual/V31__add_competition_scrap_count.sql`을 배포 전에 실행한다(컬럼·백필·인덱스).
