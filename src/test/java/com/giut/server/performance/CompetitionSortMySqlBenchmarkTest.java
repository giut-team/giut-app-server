package com.giut.server.performance;

import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.dto.request.CompetitionSort;
import com.giut.server.competition.dto.response.PublicCompetitionListResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.service.CompetitionService;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in 성능 측정: 루프백의 폐기 가능한 MySQL 스키마만 사용한다. 공유·운영 DB를 가리키게 바꾸지 않는다.
 * 실행: GIUT_PERF_MYSQL_TEST=true, 127.0.0.1:33397 root/giut-local-test, DB giut_sort_benchmark.
 * 결과 Markdown 경로: GIUT_BENCH_OUT(없으면 출력만).
 *
 * <p>같은 시드로 공모전 10,000건(공개·마감 전 9,000건), 사용자 2,000명, 스크랩 약 30만 건을 넣고
 * 공개 목록 정렬별 서비스 호출 시간과 스크랩 추가·취소 시간을 잰다. 스크랩 수 컬럼(scrap_count)이 있으면
 * 운영 백필과 같은 UPDATE로 채운 뒤 잰다. 코드 변경 전후에 이 클래스를 그대로 실행해 비교한다.
 */
@EnabledIfEnvironmentVariable(named = "GIUT_PERF_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33397/giut_sort_benchmark?useSSL=false&allowPublicKeyRetrieval=true"
                + "&createDatabaseIfNotExist=true&rewriteBatchedStatements=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false", "app.push.worker-enabled=false"
})
class CompetitionSortMySqlBenchmarkTest {
    private static final int COMPETITIONS = 10_000;
    private static final int USERS = 2_000;
    private static final int WARMUP = 10;
    private static final int RUNS = 50;
    private static final int WRITE_CYCLES = 200;

    @Autowired CompetitionService service;
    @Autowired CompetitionRepository competitions;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void measureCompetitionListSortsOnMySql() throws IOException {
        Seed seed = seed();
        boolean counterColumn = hasColumn("competitions", "scrap_count");
        String label = counterColumn ? "scrap_count 컬럼" : "COUNT 서브쿼리";
        if (counterColumn) {
            // 운영 V31 백필과 같은 방식으로 기존 스크랩 수를 채운다.
            jdbc.update("""
                    UPDATE competitions c
                    LEFT JOIN (SELECT competition_id, COUNT(*) cnt FROM competition_scraps GROUP BY competition_id) s
                      ON s.competition_id = c.id
                    SET c.scrap_count = COALESCE(s.cnt, 0)
                    """);
        }
        jdbc.execute("ANALYZE TABLE competitions, competition_scraps");

        StringBuilder report = new StringBuilder();
        report.append("## ").append(label).append(" (MySQL ").append(jdbc.queryForObject("SELECT VERSION()", String.class))
                .append(")\n\n- 공모전 ").append(COMPETITIONS).append("건(공개·마감 전 ").append(seed.published())
                .append("건), 사용자 ").append(USERS).append("명, 스크랩 ").append(seed.scraps()).append("건\n")
                .append("- 서비스 호출(`CompetitionService#getCompetitions`, 응답 조립 포함) 워밍업 ").append(WARMUP)
                .append("회 후 ").append(RUNS).append("회\n\n")
                .append("| 요청 | 중앙값(ms) | p95(ms) | 평균(ms) |\n|---|---:|---:|---:|\n");
        for (CompetitionSort sort : CompetitionSort.values()) {
            report.append(row("sort=" + sort + " page=0", () -> list(sort, 0)));
        }
        report.append(row("sort=SCRAPS page=200(깊은 페이지)", () -> list(CompetitionSort.SCRAPS, 200)));
        report.append(row("sort=SCRAPS keyword=7", () -> service.getCompetitions(
                new CompetitionSearchRequest(0, 20, "7", null, null, null, CompetitionSort.SCRAPS))));

        // 정렬 결과가 실제 스크랩 수 순서인지 확인(성능 비교의 전제).
        PublicCompetitionListResponse top = list(CompetitionSort.SCRAPS, 0);
        List<Long> counts = top.competitions().stream().map(c -> c.scrapCount()).toList();
        assertThat(counts).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(counts.getFirst()).isEqualTo(seed.maxScraps());

        report.append("\n### 스크랩 추가+취소 ").append(WRITE_CYCLES).append("회(서비스 호출)\n\n")
                .append("| 작업 | 중앙값(ms) | p95(ms) | 평균(ms) |\n|---|---:|---:|---:|\n");
        Long writer = seed.writerUserId();
        Long target = seed.hotCompetitionId();
        double[] add = new double[WRITE_CYCLES];
        double[] remove = new double[WRITE_CYCLES];
        for (int i = 0; i < WRITE_CYCLES; i++) {
            long t0 = System.nanoTime();
            service.scrapCompetition(writer, target);
            long t1 = System.nanoTime();
            service.removeCompetitionScrap(writer, target);
            long t2 = System.nanoTime();
            add[i] = (t1 - t0) / 1e6;
            remove[i] = (t2 - t1) / 1e6;
        }
        report.append(stats("스크랩 추가", add)).append(stats("스크랩 취소", remove));
        Long after = jdbc.queryForObject("SELECT COUNT(*) FROM competition_scraps WHERE competition_id = ?", Long.class, target);
        assertThat(after).isEqualTo(seed.maxScraps());
        if (counterColumn) {
            assertThat(jdbc.queryForObject("SELECT scrap_count FROM competitions WHERE id = ?", Long.class, target))
                    .isEqualTo(seed.maxScraps());
        }

        report.append("\n### EXPLAIN ANALYZE (sort=SCRAPS page=0)\n\n```\n");
        String sql = counterColumn
                ? "SELECT c.id FROM competitions c WHERE c.publication_status = 'PUBLISHED' AND c.application_end_at > NOW(6) "
                + "ORDER BY c.scrap_count DESC, c.id DESC LIMIT 20"
                : "SELECT c.id FROM competitions c WHERE c.publication_status = 'PUBLISHED' AND c.application_end_at > NOW(6) "
                + "ORDER BY (SELECT COUNT(s.id) FROM competition_scraps s WHERE s.competition_id = c.id) DESC, c.id DESC LIMIT 20";
        jdbc.queryForList("EXPLAIN ANALYZE " + sql).forEach(r -> report.append(r.values().iterator().next()).append('\n'));
        report.append("```\n");

        System.out.println("BENCH_RESULT\n" + report);
        String out = System.getenv("GIUT_BENCH_OUT");
        if (out != null && !out.isBlank()) {
            Files.writeString(Path.of(out), report.toString());
        }
    }

    /** 서로 다른 회원들이 같은 공모전을 동시에 스크랩·취소해도 카운터가 실제 행 수와 같아야 한다(원자적 UPDATE). */
    @Test
    void concurrentScrapsFromDifferentUsersKeepCounterEqualToRows() throws Exception {
        Assumptions.assumeTrue(hasColumn("competitions", "scrap_count"), "scrap_count 컬럼이 있는 코드에서만 의미가 있다");
        Instant now = Instant.now();
        Long competitionId = competitions.saveAndFlush(Competition.create("동시 스크랩", Competition.Category.WEB_MOBILE_IT,
                "주최", "대학생", "소개", now.minusSeconds(60), now.plusSeconds(86_400),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, null)).getId();
        List<User> created = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            created.add(User.createOAuthUser("concurrent-" + i + "@example.com", "c" + i, User.OAuthProvider.KAKAO, "concurrent-" + i));
        }
        List<Long> userIds = users.saveAll(created).stream().map(User::getId).toList();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> adds = new ArrayList<>();
            for (Long userId : userIds) {
                adds.add(pool.submit(() -> {
                    start.await();
                    service.scrapCompetition(userId, competitionId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : adds) f.get(60, TimeUnit.SECONDS);
            assertCounterEqualsRows(competitionId, 64);

            // 절반은 취소, 나머지 절반은 중복 스크랩을 동시에 보낸다(중복은 수를 늘리지 않는다).
            CountDownLatch mixed = new CountDownLatch(1);
            List<Future<?>> changes = new ArrayList<>();
            for (int i = 0; i < userIds.size(); i++) {
                Long userId = userIds.get(i);
                boolean remove = i % 2 == 0;
                changes.add(pool.submit(() -> {
                    mixed.await();
                    if (remove) service.removeCompetitionScrap(userId, competitionId);
                    else service.scrapCompetition(userId, competitionId);
                    return null;
                }));
            }
            mixed.countDown();
            for (Future<?> f : changes) f.get(60, TimeUnit.SECONDS);
            assertCounterEqualsRows(competitionId, 32);
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertCounterEqualsRows(Long competitionId, long expected) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competition_scraps WHERE competition_id = ?", Long.class, competitionId))
                .isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT scrap_count FROM competitions WHERE id = ?", Long.class, competitionId))
                .isEqualTo(expected);
    }

    private PublicCompetitionListResponse list(CompetitionSort sort, int page) {
        return service.getCompetitions(new CompetitionSearchRequest(page, 20, null, null, null, null, sort));
    }

    private String row(String name, Supplier<PublicCompetitionListResponse> call) {
        for (int i = 0; i < WARMUP; i++) call.get();
        double[] ms = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            PublicCompetitionListResponse response = call.get();
            ms[i] = (System.nanoTime() - start) / 1e6;
            assertThat(response.totalElements()).isPositive();
        }
        return stats(name, ms);
    }

    private static String stats(String name, double[] ms) {
        double[] sorted = ms.clone();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        double p95 = sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
        double mean = Arrays.stream(sorted).average().orElse(0);
        return String.format("| %s | %.2f | %.2f | %.2f |%n", name, median, p95, mean);
    }

    private boolean hasColumn(String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private record Seed(int published, long scraps, long maxScraps, Long hotCompetitionId, Long writerUserId) {
    }

    /** 고정 시드라 변경 전후에 같은 데이터가 만들어진다. */
    private Seed seed() {
        Random random = new Random(20261119L);
        List<User> created = new ArrayList<>();
        for (int i = 0; i < USERS + 1; i++) {
            created.add(User.createOAuthUser("bench-" + i + "@example.com", "bench" + i, User.OAuthProvider.KAKAO, "bench-" + i));
        }
        List<User> saved = users.saveAll(created);
        long[] userIds = saved.subList(0, USERS).stream().mapToLong(User::getId).toArray();
        Long writer = saved.get(USERS).getId(); // 측정용 쓰기 사용자. 시드 스크랩에는 넣지 않는다.

        Instant now = Instant.now();
        Competition template = competitions.saveAndFlush(Competition.create("벤치 공모전", Competition.Category.WEB_MOBILE_IT,
                "주최", "대학생", "소개", now.minusSeconds(86_400), now.plusSeconds(86_400),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, null));
        // 스키마에 맞춰 같은 행을 두 배씩 복제한다(컬럼 목록은 실제 테이블에서 읽는다).
        String columns = String.join(", ", jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'competitions' AND COLUMN_NAME <> 'id'
                ORDER BY ORDINAL_POSITION
                """, String.class));
        while (jdbc.queryForObject("SELECT COUNT(*) FROM competitions", Integer.class) < COMPETITIONS) {
            jdbc.update("INSERT INTO competitions (" + columns + ") SELECT " + columns + " FROM competitions");
        }
        // INSERT ... SELECT는 자동 증가 ID에 빈 구간을 남기므로 ID 범위가 아니라 순서로 앞의 COMPETITIONS건만 남긴다.
        Long lastKept = jdbc.queryForObject("SELECT id FROM competitions ORDER BY id LIMIT 1 OFFSET ?", Long.class, COMPETITIONS - 1);
        jdbc.update("DELETE FROM competitions WHERE id > ?", lastKept);
        List<Long> ids = jdbc.queryForList("SELECT id FROM competitions ORDER BY id", Long.class);
        assertThat(ids).hasSize(COMPETITIONS);

        // 10%는 비공개, 나머지는 서로 다른 마감일·조회수.
        List<Object[]> updates = new ArrayList<>();
        int published = 0;
        for (Long id : ids) {
            boolean draft = random.nextInt(10) == 0;
            if (!draft) published++;
            updates.add(new Object[]{"벤치 공모전 " + id, draft ? "DRAFT" : "PUBLISHED",
                    Timestamp.from(now.plusSeconds(3_600L + random.nextInt(60 * 86_400))), random.nextInt(5_000), id});
        }
        jdbc.batchUpdate("UPDATE competitions SET title = ?, publication_status = ?, application_end_at = ?, view_count = ? WHERE id = ?",
                updates);

        // 스크랩 수는 치우친 분포(소수 인기 공모전에 몰림). 평균 약 30, 최대 USERS.
        // 테스트 JVM 힙(512MB)을 넘지 않게 10,000건 단위로 나눠 넣는다.
        List<Object[]> scraps = new ArrayList<>();
        Timestamp createdAt = Timestamp.from(now);
        long inserted = 0;
        long max = 0;
        Long hot = null;
        for (Long id : ids) {
            double pareto = Math.pow(1 - random.nextDouble(), -1 / 1.3) - 1; // 평균 ~3.3
            int k = (int) Math.min(USERS, Math.round(9 * pareto));
            int start = random.nextInt(USERS);
            for (int j = 0; j < k; j++) {
                scraps.add(new Object[]{userIds[(start + j) % USERS], id, createdAt, createdAt});
                if (scraps.size() == 10_000) {
                    inserted += insertScraps(scraps);
                }
            }
            if (k > max) {
                max = k;
                hot = id;
            }
        }
        inserted += insertScraps(scraps);
        // 가장 인기 있는 공모전은 공개 상태로 둔다(쓰기 측정 대상).
        jdbc.update("UPDATE competitions SET publication_status = 'PUBLISHED' WHERE id = ?", hot);
        published = jdbc.queryForObject("SELECT COUNT(*) FROM competitions WHERE publication_status = 'PUBLISHED'", Integer.class);
        return new Seed(published, inserted, max, hot, writer);
    }

    private long insertScraps(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO competition_scraps (user_id, competition_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                batch);
        long size = batch.size();
        batch.clear();
        return size;
    }
}
