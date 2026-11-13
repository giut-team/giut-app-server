package com.giut.server.competition.service;

import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.entity.CompetitionExtractionJob.Status;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.competition.service.CompetitionExtractionJobStore.ClaimedJob;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.competition.util.PublicCompetitionSource;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Opt-in only: 루프백의 폐기 가능한 MySQL 스키마만 사용한다. 공유·운영 DB를 가리키게 바꾸지 않는다.
 * 실행: GIUT_EXTRACTION_MYSQL_TEST=true, 127.0.0.1:33397 root/giut-local-test, DB giut_extraction_test.
 */
@EnabledIfEnvironmentVariable(named = "GIUT_EXTRACTION_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33397/giut_extraction_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.ai-extraction.jobs.max-active-per-user=100",
        "app.ai-extraction.jobs.max-requests-per-user-per-hour=1000",
        "app.ai-extraction.jobs.max-active-total=1000"
})
class CompetitionExtractionJobMySqlTest {
    @Autowired CompetitionExtractionJobStore store;
    @Autowired CompetitionExtractionJobRepository jobs;
    @Autowired CompetitionExtractionJobService service;
    @Autowired CompetitionExtractionWorker worker;
    @Autowired CompetitionUrlNormalizer normalizer;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PublicCompetitionSource publicSource;
    @MockitoBean AiCompetitionExtractClient extractor;

    private User member;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        users.deleteAll();
        User user = User.createOAuthUser("mysql-extract@example.com", "학생", User.OAuthProvider.KAKAO, "mysql-extract");
        user.verifyUniversityEmail("mysql-extract@uos.ac.kr");
        member = users.saveAndFlush(user);
        when(extractor.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        jobs.deleteAll();
        users.deleteAll();
    }

    @Test
    void runsOnRealMySqlWithSkipLockedSupport() {
        String version = jdbc.queryForObject("select version()", String.class);
        String isolation = jdbc.queryForObject("select @@transaction_isolation", String.class);
        System.out.println("MySQL version=" + version + " isolation=" + isolation);
        assertThat(version).doesNotContainIgnoringCase("h2");
        assertThat(isolation).isEqualTo("REPEATABLE-READ");
    }

    @Test
    void concurrentWorkersClaimDisjointJobs() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 12; i++) enqueue("https://example.com/claim-" + i, now.minusSeconds(60));

        var start = new CountDownLatch(1);
        List<Future<List<ClaimedJob>>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return store.claim(5, now);
                }));
            }
            start.countDown();
            Set<Long> ids = new HashSet<>();
            Set<String> tokens = new HashSet<>();
            int total = 0;
            for (Future<List<ClaimedJob>> result : results) {
                for (ClaimedJob job : result.get(20, TimeUnit.SECONDS)) {
                    total++;
                    ids.add(job.id());
                    tokens.add(job.token());
                }
            }
            assertThat(total).isEqualTo(12);
            assertThat(ids).hasSize(12);
            assertThat(tokens).hasSize(12);
        }
        assertThat(jobs.findAll()).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
            assertThat(job.getAttemptCount()).isEqualTo(1);
        });
    }

    @Test
    void claimSkipsRowLockedByAnotherTransactionWithoutWaiting() throws Exception {
        Instant now = Instant.now();
        Long locked = enqueue("https://example.com/locked", now.minusSeconds(120));
        Long free = enqueue("https://example.com/free", now.minusSeconds(60));

        var lockHeld = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(1)) {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                assertThat(jobs.findDuePendingIds(now, 1)).containsExactly(locked);
                assertThat(jobs.lockIfAvailable(locked)).isPresent();
                lockHeld.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                status.setRollbackOnly();
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            List<ClaimedJob> claimed = store.claim(5, now);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);

            assertThat(claimed).extracting(ClaimedJob::id).containsExactly(free);
            assertThat(elapsedMs).isLessThan(5_000); // innodb_lock_wait_timeout(기본 50초)를 기다리지 않았다.
        }
        assertThat(jobs.findById(locked).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void openClaimTransactionLocksOnlyTheRowsItTakes() throws Exception {
        // 회귀: 한 단계 ORDER BY+LIMIT FOR UPDATE SKIP LOCKED는 작은 테이블에서 전체 스캔으로 모든 대기 행을 잠가 0건이 됐다.
        Instant now = Instant.now();
        for (int i = 0; i < 6; i++) enqueue("https://example.com/open-claim-" + i, now.minusSeconds(60 - i));

        var claimedInOpenTx = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(1)) {
            Future<List<ClaimedJob>> holder = executor.submit(() -> transactions.execute(status -> {
                List<ClaimedJob> first = store.claim(1, now);
                claimedInOpenTx.countDown();
                try {
                    assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return first;
            }));
            assertThat(claimedInOpenTx.await(10, TimeUnit.SECONDS)).isTrue();
            List<ClaimedJob> others = store.claim(10, now);
            release.countDown();
            List<ClaimedJob> first = holder.get(10, TimeUnit.SECONDS);

            assertThat(first).hasSize(1);
            assertThat(others).hasSize(5);
            assertThat(others).extracting(ClaimedJob::id).doesNotContain(first.getFirst().id());
        }
    }

    @Test
    void leaseTakeoverFencesLateResultsEvenWhenRacingRecovery() throws Exception {
        for (int round = 0; round < 15; round++) {
            Instant now = Instant.now();
            Long id = enqueue("https://example.com/race-" + round, now.minusSeconds(1));
            ClaimedJob oldRun = store.claim(1, now).getFirst();
            Instant afterLease = now.plus(Duration.ofSeconds(181));

            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Future<Boolean> save = executor.submit(() -> {
                    start.await();
                    return store.recordSuccess(oldRun, "{\"title\":\"late\"}", afterLease);
                });
                Future<Integer> recover = executor.submit(() -> {
                    start.await();
                    return store.recoverExpiredLeases(afterLease);
                });
                start.countDown();
                boolean saved = save.get(20, TimeUnit.SECONDS);
                int recovered = recover.get(20, TimeUnit.SECONDS);
                CompetitionExtractionJob job = jobs.findById(id).orElseThrow();
                // 정확히 한쪽만 이긴다: 저장이 먼저면 SUCCEEDED, 회수가 먼저면 PENDING이고 옛 결과는 없다.
                assertThat(saved ^ (recovered == 1)).isTrue();
                if (saved) {
                    assertThat(job.getStatus()).isEqualTo(Status.SUCCEEDED);
                    assertThat(job.getResultJson()).contains("late");
                } else {
                    assertThat(job.getStatus()).isEqualTo(Status.PENDING);
                    assertThat(job.getResultJson()).isNull();
                    ClaimedJob newRun = store.claim(1, afterLease.plus(Duration.ofMinutes(5))).getFirst();
                    assertThat(store.recordSuccess(oldRun, "{\"title\":\"late\"}", Instant.now())).isFalse();
                    assertThat(store.recordSuccess(newRun, "{\"title\":\"fresh\"}", Instant.now())).isTrue();
                    assertThat(jobs.findById(id).orElseThrow().getResultJson()).contains("fresh");
                }
            }
        }
    }

    @Test
    void retryBackoffAndFinalFailureUseCorrectTimeComparisons() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long id = enqueue("https://example.com/retry", now);
        ClaimedJob first = store.claim(5, now).getFirst();
        assertThat(store.recordFailure(first, "AI_UNAVAILABLE", "잠시 후 다시", null, true, now)).isTrue();
        assertThat(jobs.findById(id).orElseThrow().getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        assertThat(store.claim(5, now.plusSeconds(29))).isEmpty();
        ClaimedJob second = store.claim(5, now.plusSeconds(30)).getFirst();
        assertThat(store.recordFailure(second, "AI_UNAVAILABLE", "잠시 후 다시", null, true, now.plusSeconds(30))).isTrue();
        assertThat(store.claim(5, now.plusSeconds(89))).isEmpty();
        ClaimedJob third = store.claim(5, now.plusSeconds(90)).getFirst();
        assertThat(store.recordFailure(third, "AI_UNAVAILABLE", "잠시 후 다시", null, true, now.plusSeconds(90))).isTrue();
        CompetitionExtractionJob failed = jobs.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getActiveKey()).isNull();
        assertThat(store.claim(5, now.plus(Duration.ofHours(1)))).isEmpty();
    }

    @Test
    void restartRecoveryRequeuesAbandonedRunAndExhaustedRunFails() {
        Instant now = Instant.now();
        Long id = enqueue("https://example.com/restart", now);
        // 실행 중 서버가 죽었다고 가정: RUNNING만 남고 결과 저장은 오지 않는다.
        store.claim(5, now);
        Instant t = now;
        for (int attempt = 1; attempt <= 3; attempt++) {
            t = t.plus(Duration.ofSeconds(181));
            assertThat(store.recoverExpiredLeases(t)).isEqualTo(1);
            CompetitionExtractionJob job = jobs.findById(id).orElseThrow();
            if (attempt < 3) {
                assertThat(job.getStatus()).isEqualTo(Status.PENDING);
                t = job.getNextAttemptAt();
                assertThat(store.claim(5, t)).hasSize(1);
            } else {
                assertThat(job.getStatus()).isEqualTo(Status.FAILED);
                assertThat(job.getErrorCode()).isEqualTo("LEASE_EXPIRED");
            }
        }
    }

    @Test
    void claimExpiresOverduePendingInsteadOfRunningItEvenBeforeMaintenance() {
        // 재시작 직후처럼 대기 만료 정리(expireStalePending)보다 선점이 먼저 실행되는 상황. cutoff = now - 15분.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long oldest = enqueue("https://example.com/stale-old", now.minus(Duration.ofMinutes(20)));
        Long atCutoff = enqueue("https://example.com/stale-cutoff", now.minus(Duration.ofMinutes(15)));
        Long justInside = enqueue("https://example.com/fresh-edge", now.minus(Duration.ofMinutes(15)).plus(1, ChronoUnit.MICROS));
        Long fresh = enqueue("https://example.com/fresh", now.minusSeconds(1));

        List<ClaimedJob> claimed = store.claim(2, now);

        assertThat(claimed).extracting(ClaimedJob::id).containsExactlyInAnyOrder(justInside, fresh);
        assertThat(claimed).allSatisfy(job -> assertThat(job.attemptCount()).isEqualTo(1));
        for (Long id : List.of(oldest, atCutoff)) {
            CompetitionExtractionJob expired = jobs.findById(id).orElseThrow();
            assertThat(expired.getStatus()).isEqualTo(Status.FAILED);
            assertThat(expired.getErrorCode()).isEqualTo("QUEUE_TIMEOUT");
            assertThat(expired.getAttemptCount()).isZero();
            assertThat(expired.getLeaseToken()).isNull();
            assertThat(expired.getActiveKey()).isNull();
            assertThat(expired.getCompletedAt()).isEqualTo(now);
        }
        assertThat(store.expireStalePending(now)).isZero(); // 이미 선점 tx가 정리했다.
    }

    @Test
    void concurrentClaimersExpireOverdueOnceAndClaimFreshJobsDisjointly() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Set<Long> stale = new HashSet<>();
        Set<Long> fresh = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            stale.add(enqueue("https://example.com/race-stale-" + i, now.minus(Duration.ofMinutes(16)).plusSeconds(i)));
            fresh.add(enqueue("https://example.com/race-fresh-" + i, now.minusSeconds(60 - i)));
        }

        var start = new CountDownLatch(1);
        List<Future<List<ClaimedJob>>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return store.claim(2, now);
                }));
            }
            start.countDown();
            List<Long> claimedIds = new ArrayList<>();
            for (Future<List<ClaimedJob>> result : results) {
                result.get(20, TimeUnit.SECONDS).forEach(job -> claimedIds.add(job.id()));
            }
            assertThat(claimedIds).doesNotHaveDuplicates();
            assertThat(fresh).containsAll(claimedIds);
        }
        // 한 번에 잡지 못한 후보가 남을 수 있으므로 한 번 더 선점하면 남은 신규 작업만 가져가고 만료 작업은 남지 않는다.
        store.claim(8, now);
        assertThat(store.expireStalePending(now)).isZero();
        assertThat(jobs.findAllById(stale)).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(Status.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("QUEUE_TIMEOUT");
            assertThat(job.getAttemptCount()).isZero();
        });
        assertThat(jobs.findAllById(fresh)).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(Status.RUNNING);
            assertThat(job.getAttemptCount()).isEqualTo(1);
        });
    }

    @Test
    void concurrentSameUserSameUrlCreatesOneJob() throws Exception {
        for (int round = 0; round < 10; round++) {
            String url = "https://example.com/dup-" + round;
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(3)) {
                List<Future<Long>> ids = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    ids.add(executor.submit(() -> {
                        start.await();
                        return service.accept(member.getId(), url).response().jobId();
                    }));
                }
                start.countDown();
                Set<Long> distinct = new HashSet<>();
                for (Future<Long> id : ids) distinct.add(id.get(20, TimeUnit.SECONDS));
                assertThat(distinct).hasSize(1);
            }
        }
        assertThat(jobs.count()).isEqualTo(10);
    }

    @Test
    void concurrentDifferentUsersSameUrlShareOneJobAndEveryoneCanReadIt() throws Exception {
        List<User> requesters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User user = User.createOAuthUser("mysql-shared-" + i + "@example.com", "공유" + i,
                    User.OAuthProvider.KAKAO, "mysql-shared-" + i);
            user.verifyUniversityEmail("mysql-shared-" + i + "@uos.ac.kr");
            requesters.add(users.saveAndFlush(user));
        }
        for (int round = 0; round < 10; round++) {
            String url = "https://example.com/shared-" + round;
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(requesters.size())) {
                List<Future<Long>> ids = new ArrayList<>();
                for (User requester : requesters) {
                    ids.add(executor.submit(() -> {
                        start.await();
                        // active_key UNIQUE에 걸린 쪽은 예외 없이 먼저 commit된 작업에 합류해야 한다.
                        return service.accept(requester.getId(), url).response().jobId();
                    }));
                }
                start.countDown();
                Set<Long> distinct = new HashSet<>();
                for (Future<Long> id : ids) distinct.add(id.get(20, TimeUnit.SECONDS));
                assertThat(distinct).hasSize(1);
                Long jobId = distinct.iterator().next();
                for (User requester : requesters) {
                    assertThat(service.get(requester.getId(), jobId).jobId()).isEqualTo(jobId);
                }
            }
        }
        assertThat(jobs.count()).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from competition_extraction_job_subscriptions", Long.class))
                .isEqualTo(40L);
        assertThat(jdbc.queryForObject("""
                select count(*) from competition_extraction_jobs where status in ('PENDING','RUNNING')
                """, Long.class)).isEqualTo(10L);
    }

    @Test
    void workerHoldsNoDatabaseConnectionWhileAiCallIsInFlight() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        var inAi = new CountDownLatch(1);
        var finishAi = new CountDownLatch(1);
        AtomicInteger activeDuringAi = new AtomicInteger(-1);
        when(extractor.extract(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            activeDuringAi.set(hikari.getHikariPoolMXBean().getActiveConnections());
            inAi.countDown();
            assertThat(finishAi.await(20, TimeUnit.SECONDS)).isTrue();
            return new AiCompetitionExtractClient.Extraction("공모전", null, null, null, null,
                    LocalDate.of(2099, 1, 2), null, null, "CONTEST", List.of("IDEA"), null, "소개", null, null);
        });
        Long id = service.accept(member.getId(), "https://example.com/no-connection").response().jobId();

        assertThat(worker.pollOnce()).isEqualTo(1);
        assertThat(inAi.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(activeDuringAi.get()).isZero();
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(Status.RUNNING);
        assertThat(worker.pollOnce()).isZero(); // 대기 작업이 없고, 실행 중 작업은 다시 선점되지 않는다.
        finishAi.countDown();

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && !jobs.findById(id).orElseThrow().isTerminal()) Thread.sleep(20);
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(Status.SUCCEEDED);
        assertThat(service.get(member.getId(), id).result().draft().title()).isEqualTo("공모전");
    }

    private Long enqueue(String url, Instant at) {
        return store.enqueue(member.getId(), url, normalizer.normalize(url).hash(), at).job().getId();
    }
}
