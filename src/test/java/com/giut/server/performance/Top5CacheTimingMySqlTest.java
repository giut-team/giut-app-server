package com.giut.server.performance;

import com.giut.server.competition.dto.response.PublicCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.service.CompetitionService;
import com.giut.server.competition.service.CompetitionTop5Cache;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top5 캐시 적용 전후 요청 처리 시간을 MySQL에서 비교한다. RUN_TOP5_PERF=true일 때만 실행한다.
 * 스키마를 만들고 지우므로 일회용 loopback MySQL(127.0.0.1:33390/giut_top5_perf)에만 연결한다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_TOP5_PERF", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33390/giut_top5_perf?useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "app.ai-extraction.jobs.worker-enabled=false", "app.push.worker-enabled=false",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "logging.level.org.hibernate.stat=OFF"
})
class Top5CacheTimingMySqlTest {
    private static final Path REPORT_PATH = Path.of("build", "reports", "performance", "top5-cache-timing.md");

    // CompetitionRepository#findTop10ByPopularity와 같은 SQL. 실행 계획(EXPLAIN) 기록용이다.
    private static final String RANKING_SQL = """
            SELECT competition.*
            FROM competitions competition
            LEFT JOIN competition_scraps scrap ON scrap.competition_id = competition.id
            WHERE competition.publication_status = ?
              AND competition.application_end_at > ?
            GROUP BY competition.id
            ORDER BY (0.3 * LOG(1 + competition.view_count)
                     + 0.7 * LOG(1 + COUNT(scrap.id))) DESC,
                     competition.view_count DESC,
                     COUNT(scrap.id) DESC,
                     competition.id DESC
            LIMIT 10
            """;

    private static final int COMPETITIONS = intEnv("TOP5_PERF_COMPETITIONS", 50_000);
    private static final int USERS = intEnv("TOP5_PERF_USERS", 1_100);
    private static final int SCRAPS_PER_COMPETITION = intEnv("TOP5_PERF_SCRAPS_PER_COMPETITION", 11);
    private static final int UNCACHED_ITERATIONS = intEnv("TOP5_PERF_UNCACHED_ITERATIONS", 30);
    private static final int CACHED_ITERATIONS = intEnv("TOP5_PERF_CACHED_ITERATIONS", 500);

    @Autowired CompetitionService competitionService;
    @Autowired CompetitionTop5Cache top5Cache;
    @Autowired CompetitionRepository competitions;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void comparesTop5RequestTimeWithAndWithoutCache() throws IOException {
        assertThat(SCRAPS_PER_COMPETITION).isLessThanOrEqualTo(USERS);
        long seedStartedAt = System.nanoTime();
        seed();
        double seedSeconds = (System.nanoTime() - seedStartedAt) / 1e9;
        long competitionRows = count("competitions");
        long scrapRows = count("competition_scraps");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        statistics.clear();
        List<PublicCompetitionResponse> uncachedResult = competitionService.getTop10Candidates();
        long uncachedStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        top5Cache.refresh();
        long refreshStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        List<PublicCompetitionResponse> cachedResult = top5Cache.getPublishedTop5();
        long cachedStatements = statistics.getPrepareStatementCount();

        assertThat(uncachedResult).hasSize(10);
        assertThat(cachedResult).extracting(PublicCompetitionResponse::id)
                .containsExactlyElementsOf(uncachedResult.stream().limit(5).map(PublicCompetitionResponse::id).toList());

        statistics.setStatisticsEnabled(false);
        Timing uncached = time(3, UNCACHED_ITERATIONS, competitionService::getTop10Candidates);
        Timing refresh = time(1, 5, () -> {
            top5Cache.refresh();
            return null;
        });
        Timing cached = time(20, CACHED_ITERATIONS, top5Cache::getPublishedTop5);
        Object[] rankingParams = {Competition.PublicationStatus.PUBLISHED.name(), Timestamp.from(Instant.now())};
        List<Map<String, Object>> plan = jdbc.queryForList("EXPLAIN FORMAT=TRADITIONAL " + RANKING_SQL, rankingParams);
        String analyze = String.join("\n", jdbc.queryForList("EXPLAIN ANALYZE " + RANKING_SQL, String.class, rankingParams));

        String report = report(competitionRows, scrapRows, seedSeconds, uncached, cached, refresh,
                uncachedStatements, cachedStatements, refreshStatements, plan, analyze);
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report);
        System.out.println(report);
        dropHelperTables();
    }

    private void seed() {
        Instant now = Instant.now();
        Long templateId = transactions.execute(status -> {
            for (int i = 0; i < USERS; i++) {
                users.save(User.createOAuthUser("perf-top5-" + i + "@giut.local", "perf" + i,
                        User.OAuthProvider.KAKAO, "perf-top5-" + i));
            }
            return competitions.save(Competition.create("perf-template", Competition.Category.WEB_MOBILE_IT,
                    "주최", "대학생", "소개", now.minus(1, DAYS), now.plus(30, DAYS),
                    Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED,
                    null)).getId();
        });

        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'competitions' AND column_name <> 'id'
                ORDER BY ordinal_position
                """, String.class);
        String copiedValues = columns.stream().map(column -> switch (column) {
            case "title" -> "CONCAT('perf-competition-', s.n)";
            case "view_count" -> "MOD(s.n * 7919, 5000)";
            default -> "t." + column;
        }).collect(Collectors.joining(", "));
        // 사용자 i번째 순번 간격을 두어 같은 공모전에 같은 사용자가 두 번 스크랩하지 않게 한다(unique 제약).
        int userStride = USERS / SCRAPS_PER_COMPETITION;

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS perf_digit, perf_seq, perf_user_idx");
                statement.execute("CREATE TABLE perf_digit (d INT PRIMARY KEY)");
                statement.execute("INSERT INTO perf_digit VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)");
                statement.execute("CREATE TABLE perf_seq (n INT PRIMARY KEY)");
                statement.execute("""
                        INSERT INTO perf_seq
                        SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + f.d * 100000
                        FROM perf_digit a, perf_digit b, perf_digit c, perf_digit d, perf_digit e, perf_digit f
                        """);
                statement.execute("INSERT INTO competitions (" + String.join(", ", columns) + ") SELECT "
                        + copiedValues + " FROM competitions t JOIN perf_seq s ON s.n < " + (COMPETITIONS - 1)
                        + " WHERE t.id = " + templateId);
                statement.execute("CREATE TABLE perf_user_idx (idx INT PRIMARY KEY, user_id BIGINT NOT NULL)");
                statement.execute("INSERT INTO perf_user_idx SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id FROM users");
                statement.execute("""
                        INSERT INTO competition_scraps (user_id, competition_id, created_at, updated_at)
                        SELECT u.user_id, c.id, NOW(6), NOW(6)
                        FROM competitions c
                        JOIN perf_seq j ON j.n < %d
                        JOIN perf_user_idx u ON u.idx = MOD(c.id * 7 + j.n * %d, %d)
                        """.formatted(SCRAPS_PER_COMPETITION, userStride, USERS));
                statement.execute("ANALYZE TABLE competitions, competition_scraps");
            }
            return null;
        });
    }

    private void dropHelperTables() {
        jdbc.execute("DROP TABLE IF EXISTS perf_digit, perf_seq, perf_user_idx");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static Timing time(int warmUp, int iterations, Supplier<?> operation) {
        for (int i = 0; i < warmUp; i++) {
            operation.get();
        }
        double[] millis = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            long startedAt = System.nanoTime();
            operation.get();
            millis[i] = (System.nanoTime() - startedAt) / 1_000_000.0;
        }
        return Timing.of(millis);
    }

    private static String report(long competitionRows, long scrapRows, double seedSeconds, Timing uncached,
                                 Timing cached, Timing refresh, long uncachedStatements, long cachedStatements,
                                 long refreshStatements, List<Map<String, Object>> plan, String analyze) {
        StringBuilder out = new StringBuilder();
        out.append("# Top5 캐시 적용 전후 요청 시간 비교\n\n");
        out.append("측정 시각: ").append(Instant.now()).append("  \n");
        out.append("DB: 로컬 일회용 MySQL ").append("(loopback, 단일 스레드 순차 호출, 네트워크·HTTP 직렬화 제외)\n\n");
        out.append("| 데이터 | 건수 |\n|---|---:|\n");
        out.append("| 게시·모집 중 공모전 | %,d |\n".formatted(competitionRows));
        out.append("| 스크랩 | %,d |\n".formatted(scrapRows));
        out.append("\n데이터 생성 시간: %.1f초\n\n".formatted(seedSeconds));
        out.append("## 요청 1회 처리 시간 (ms)\n\n");
        out.append("| 경로 | 호출 수 | 평균 | p50 | p95 | 최대 | 호출당 SQL 문 |\n|---|---:|---:|---:|---:|---:|---:|\n");
        out.append(uncached.row("캐시 없음: 요청마다 순위 계산", uncachedStatements));
        out.append(cached.row("캐시 적용: 스냅샷 + 게시 상태 확인", cachedStatements));
        out.append(refresh.row("캐시 갱신 1회 (10분마다 실행)", refreshStatements));
        out.append("\n평균 기준 %.1f배 빠름 (%.2fms → %.2fms, %.1f%% 감소)  \n".formatted(
                uncached.avg() / cached.avg(), uncached.avg(), cached.avg(),
                (1 - cached.avg() / uncached.avg()) * 100));
        out.append("p95 기준 %.1f배 빠름 (%.2fms → %.2fms)\n\n".formatted(
                uncached.p95() / cached.p95(), uncached.p95(), cached.p95()));
        out.append("## 순위 쿼리 실행 계획 (EXPLAIN)\n\n");
        out.append("| table | type | key | rows | filtered | Extra |\n|---|---|---|---:|---:|---|\n");
        for (Map<String, Object> row : plan) {
            out.append("| %s | %s | %s | %s | %s | %s |\n".formatted(row.get("table"), row.get("type"),
                    row.get("key"), row.get("rows"), row.get("filtered"), row.get("Extra")));
        }
        out.append("\n## EXPLAIN ANALYZE\n\n```text\n").append(analyze).append("\n```\n");
        return out.toString();
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private record Timing(int count, double avg, double p50, double p95, double max) {
        static Timing of(double[] millis) {
            double[] sorted = millis.clone();
            Arrays.sort(sorted);
            return new Timing(sorted.length, Arrays.stream(sorted).average().orElse(0),
                    percentile(sorted, 0.50), percentile(sorted, 0.95), sorted[sorted.length - 1]);
        }

        private static double percentile(double[] sorted, double quantile) {
            int index = (int) Math.ceil(quantile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }

        String row(String label, long statements) {
            return "| %s | %d | %.2f | %.2f | %.2f | %.2f | %d |\n".formatted(label, count, avg, p50, p95, max, statements);
        }
    }
}
