package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.entity.CompetitionExtractionJob.Status;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.competition.service.CompetitionExtractionJobStore.ClaimedJob;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.competition.util.PublicCompetitionSource;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H2(MySQL 모드)·실제 Spring Security/JWT·MockMvc·commit된 상태로 비동기 추출 계약을 검증한다.
 * H2의 행 잠금은 MySQL InnoDB SKIP LOCKED 동작을 보장하지 않으므로 동시 선점은 CompetitionExtractionJobMySqlTest가 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_extraction_jobs;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.ai-extraction.jobs.max-active-per-user=2",
        "app.ai-extraction.jobs.max-requests-per-user-per-hour=4",
        "app.ai-extraction.jobs.max-active-total=3"
})
class CompetitionExtractionJobPersistenceTest {
    private static final String EXTRACTIONS = "/api/competitions/extractions";
    private static final String ADMIN = "/api/admin/competitions";
    private static final String SOURCE = "https://example.com/contest";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionUrlRepository urls;
    @Autowired private CompetitionVerificationLogRepository logs;
    @Autowired private CompetitionExtractionJobRepository jobs;
    @Autowired private CompetitionExtractionJobStore store;
    @Autowired private CompetitionExtractionWorker worker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompetitionUrlNormalizer normalizer;
    @Autowired private DataSource dataSource;
    @MockitoBean private PublicCompetitionSource publicSource;
    @MockitoBean private AiCompetitionExtractClient extractor;

    private MockMvc mvc;
    private User admin;
    private User member;
    private User other;
    private User unverified;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("extract-admin@example.com", "hash", "관리자"));
        member = verified("extract-member", "extract-member@uos.ac.kr");
        other = verified("extract-other", "extract-other@uos.ac.kr");
        unverified = users.saveAndFlush(User.createOAuthUser("extract-unverified@example.com", "미인증",
                User.OAuthProvider.KAKAO, "extract-unverified"));
        when(extractor.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        jobs.deleteAll();
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @Test
    void newUrlIsCommittedAs202ThenWorkerRunsAiOutsideTransactionAndOwnerReadsDraft() throws Exception {
        AtomicReference<Boolean> transactionActive = new AtomicReference<>();
        CountDownLatch inAi = new CountDownLatch(1);
        CountDownLatch finishAi = new CountDownLatch(1);
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        when(extractor.extract(eq(SOURCE), any(Duration.class))).thenAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            inAi.countDown();
            assertThat(finishAi.await(10, TimeUnit.SECONDS)).isTrue();
            return extraction(LocalDate.of(2099, 1, 2));
        });

        MvcResult accepted = mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"  " + SOURCE + "  \"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sourceUrl").value(SOURCE))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn();
        long jobId = objectMapper.readTree(accepted.getResponse().getContentAsString()).get("jobId").asLong();
        assertThat(accepted.getResponse().getHeader("Location")).isEqualTo(EXTRACTIONS + "/" + jobId);
        // 응답 시점에 이미 commit돼 다른 트랜잭션(리포지토리 호출)에서 보인다.
        assertThat(jobs.findById(jobId)).get().extracting(CompetitionExtractionJob::getStatus).isEqualTo(Status.PENDING);
        verify(extractor, never()).extract(anyString(), any(Duration.class));

        mvc.perform(as(get(EXTRACTIONS + "/" + jobId), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(worker.pollOnce()).isEqualTo(1);
        assertThat(inAi.await(10, TimeUnit.SECONDS)).isTrue();
        // AI 호출 중: 트랜잭션·DB 커넥션을 잡고 있지 않고, RUNNING 선점은 이미 commit돼 있다.
        assertThat(transactionActive.get()).isFalse();
        assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(Status.RUNNING);
        mvc.perform(as(get(EXTRACTIONS + "/" + jobId), member))
                .andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.attemptCount").value(1));
        assertThat(worker.pollOnce()).isZero();
        finishAi.countDown();
        awaitTerminal(jobId);
        // 접수 시 1회 + 실행 직전 DNS 재확인 1회.
        verify(publicSource, times(2)).requirePublicAddress(SOURCE);

        mvc.perform(as(get(EXTRACTIONS + "/" + jobId), member))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.result.status").value("READY"))
                .andExpect(jsonPath("$.result.sourceUrl").value(SOURCE))
                .andExpect(jsonPath("$.result.draft.category").value("WEB_MOBILE_IT"))
                .andExpect(jsonPath("$.result.draft.applicationStartAt").value("2098-12-31T15:00:00Z"))
                .andExpect(jsonPath("$.result.draft.applicationEndAt").value("2099-01-02T14:59:59Z"))
                .andExpect(jsonPath("$.result.draft.urls[0].primary").value(true));

        // IDOR: 다른 회원은 존재 여부도 알 수 없다.
        mvc.perform(as(get(EXTRACTIONS + "/" + jobId), other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(as(get(EXTRACTIONS + "/" + (jobId + 999)), member)).andExpect(status().isNotFound());
        mvc.perform(as(get(EXTRACTIONS + "/abc"), member)).andExpect(status().isBadRequest());

        // 같은 사용자의 같은 URL 재요청은 보관 중인 성공 결과를 그대로 재사용한다(AI 재호출 없음).
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://EXAMPLE.COM:443/contest/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        // 다른 사용자는 결과를 공유받지 않고 자기 작업을 만든다.
        mvc.perform(json(post(EXTRACTIONS), other, "{\"url\":\"" + SOURCE + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(org.hamcrest.Matchers.not((int) jobId)));
        assertThat(jobs.count()).isEqualTo(2);
    }

    @Test
    void existingUrlKeepsExistsMeaningWithoutJobDnsOrAi() throws Exception {
        mvc.perform(json(post(ADMIN), admin, """
                {"title":"기존 공고","category":"WEB_MOBILE_IT","summary":"소개","publicationStatus":"PUBLISHED",
                 "applicationEndAt":"2099-01-01T00:00:00Z",
                 "urls":[{"type":"RECRUITMENT","url":"https://example.com/contest","primary":true}]}
                """)).andExpect(status().isCreated());

        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://EXAMPLE.COM:443/contest/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXISTS"))
                .andExpect(jsonPath("$.url").value(SOURCE))
                .andExpect(jsonPath("$.jobId").doesNotExist())
                .andExpect(header().doesNotExist("Location"));
        assertThat(jobs.count()).isZero();
        verifyNoInteractions(publicSource, extractor);
    }

    @Test
    void authenticationVerificationAndInputValidationAreKept() throws Exception {
        String body = "{\"url\":\"" + SOURCE + "\"}";
        mvc.perform(post(EXTRACTIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(EXTRACTIONS + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(json(post(EXTRACTIONS), unverified, body)).andExpect(status().isForbidden());
        mvc.perform(as(get(EXTRACTIONS + "/1"), unverified)).andExpect(status().isForbidden());
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://example.com/" + "a".repeat(2000) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"ftp://example.com/x\"}"))
                .andExpect(status().isBadRequest());

        doThrow(new IllegalArgumentException("공개 인터넷 공고 URL만 추출할 수 있습니다."))
                .when(publicSource).requirePublicAddress("https://internal.example.com/x");
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://internal.example.com/x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("공개 인터넷 공고 URL만 추출할 수 있습니다."));

        when(extractor.isConfigured()).thenReturn(false);
        mvc.perform(json(post(EXTRACTIONS), member, body)).andExpect(status().isServiceUnavailable());
        assertThat(jobs.count()).isZero();
    }

    @Test
    void sameUrlInFlightIsReusedAndPerUserAndGlobalLimitsRejectWithRetryAfter() throws Exception {
        long first = acceptedJobId(member, SOURCE);
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://example.com/contest/\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value(first));
        acceptedJobId(member, "https://example.com/second");
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://example.com/third\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"));

        acceptedJobId(other, "https://example.com/other-one");
        mvc.perform(json(post(EXTRACTIONS), other, "{\"url\":\"https://example.com/other-two\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value(503));
        assertThat(jobs.count()).isEqualTo(3);
    }

    @Test
    void hourlyCreationLimitCountsFinishedJobsToo() throws Exception {
        when(extractor.extract(anyString(), any(Duration.class))).thenThrow(
                new AiCompetitionExtractClient.ExtractionUnavailable(HttpStatus.BAD_GATEWAY, "x", false));
        for (int i = 0; i < 4; i++) {
            long id = acceptedJobId(member, "https://example.com/hourly-" + i);
            assertThat(worker.pollOnce()).isEqualTo(1);
            awaitTerminal(id);
        }
        mvc.perform(json(post(EXTRACTIONS), member, "{\"url\":\"https://example.com/hourly-5\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void transientFailuresRetryWithBackoffUntilFinalFailureWithSafeMessage() throws Exception {
        long id = acceptedJobId(member, SOURCE);
        Instant t0 = Instant.now();
        when(extractor.extract(eq(SOURCE), any(Duration.class))).thenThrow(
                new AiCompetitionExtractClient.ExtractionUnavailable(HttpStatus.GATEWAY_TIMEOUT,
                        "공모전 추출 응답 시간이 초과됐습니다.", true));

        ClaimedJob attempt1 = single(store.claim(5, t0));
        worker.execute(attempt1);
        CompetitionExtractionJob afterFirst = jobs.findById(id).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(Status.PENDING);
        assertThat(afterFirst.getErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(afterFirst.getLeaseToken()).isNull();
        assertThat(afterFirst.getNextAttemptAt()).isAfter(t0.plusSeconds(29));
        assertThat(store.claim(5, t0.plusSeconds(10))).isEmpty();
        mvc.perform(as(get(EXTRACTIONS + "/" + id), member))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.error").doesNotExist());

        ClaimedJob attempt2 = single(store.claim(5, t0.plus(Duration.ofMinutes(10))));
        assertThat(attempt2.attemptCount()).isEqualTo(2);
        worker.execute(attempt2);
        assertThat(jobs.findById(id).orElseThrow().getNextAttemptAt()).isAfter(Instant.now().plusSeconds(59));

        // 재시도도 접수 시각 기준 pending-timeout(15분) 안에서만 선점된다. 그 뒤면 QUEUE_TIMEOUT으로 끝난다.
        ClaimedJob attempt3 = single(store.claim(5, t0.plus(Duration.ofMinutes(14))));
        worker.execute(attempt3);
        mvc.perform(as(get(EXTRACTIONS + "/" + id), member))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attemptCount").value(3))
                .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.error.message").value("공모전 추출 응답 시간이 초과됐습니다."));
        CompetitionExtractionJob failed = jobs.findById(id).orElseThrow();
        assertThat(failed.getActiveKey()).isNull();
        assertThat(failed.getExpiresAt()).isAfter(failed.getCompletedAt());

        // 최종 실패 뒤에는 같은 URL로 새 작업을 만들 수 있다.
        assertThat(acceptedJobId(member, SOURCE)).isNotEqualTo(id);
    }

    @Test
    void permanentFailuresAndUnexpectedErrorsFailImmediatelyWithoutLeakingInternals() throws Exception {
        long rejected = acceptedJobId(member, "https://example.com/rejected");
        when(extractor.extract(eq("https://example.com/rejected"), any(Duration.class))).thenThrow(
                new AiCompetitionExtractClient.ExtractionUnavailable(HttpStatus.BAD_GATEWAY, "공모전 추출 서비스 응답을 받지 못했습니다.", false));
        worker.execute(single(store.claim(5, Instant.now())));
        assertThat(jobs.findById(rejected).orElseThrow()).satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(Status.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("AI_REJECTED");
            assertThat(job.getAttemptCount()).isEqualTo(1);
        });

        long broken = acceptedJobId(member, "https://example.com/broken");
        when(extractor.extract(eq("https://example.com/broken"), any(Duration.class)))
                .thenThrow(new IllegalStateException("db password=secret at line 3"));
        worker.execute(single(store.claim(5, Instant.now())));
        mvc.perform(as(get(EXTRACTIONS + "/" + broken), member))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("공모전 정보를 추출하지 못했습니다."));
    }

    @Test
    void executionRechecksPublicAddressBeforeCallingAi() throws Exception {
        long id = acceptedJobId(member, SOURCE);
        doThrow(new IllegalArgumentException("공개 인터넷 공고 URL만 추출할 수 있습니다."))
                .when(publicSource).requirePublicAddress(SOURCE);
        worker.execute(single(store.claim(5, Instant.now())));
        assertThat(jobs.findById(id).orElseThrow().getErrorCode()).isEqualTo("INVALID_SOURCE");
        verify(extractor, never()).extract(anyString(), any(Duration.class));
    }

    @Test
    void expiredLeaseIsRecoveredAndLateResponseFromOldRunCannotOverwrite() throws Exception {
        long id = acceptedJobId(member, SOURCE);
        Instant t0 = Instant.now();
        ClaimedJob oldRun = single(store.claim(5, t0));

        assertThat(store.recoverExpiredLeases(t0.plusSeconds(179))).isZero();
        assertThat(store.recoverExpiredLeases(t0.plusSeconds(181))).isEqualTo(1);
        CompetitionExtractionJob recovered = jobs.findById(id).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(Status.PENDING);
        assertThat(recovered.getErrorCode()).isEqualTo("LEASE_EXPIRED");

        ClaimedJob newRun = single(store.claim(5, t0.plus(Duration.ofMinutes(10))));
        assertThat(newRun.token()).isNotEqualTo(oldRun.token());
        assertThat(store.recordSuccess(oldRun, "{\"title\":\"옛 응답\"}", Instant.now())).isFalse();
        assertThat(store.recordFailure(oldRun, "AI_REJECTED", "x", false, Instant.now())).isFalse();
        assertThat(store.recordSuccess(newRun, "{\"title\":\"새 응답\"}", Instant.now())).isTrue();
        assertThat(store.recordSuccess(newRun, "{\"title\":\"중복 저장\"}", Instant.now())).isFalse();
        assertThat(jobs.findById(id).orElseThrow().getResultJson()).contains("새 응답");
    }

    @Test
    void stalePendingExpiresAndExpiredTerminalJobsArePurged() throws Exception {
        long id = acceptedJobId(member, SOURCE);
        Instant t0 = Instant.now();
        assertThat(store.expireStalePending(t0.plus(Duration.ofMinutes(14)))).isZero();
        assertThat(store.expireStalePending(t0.plus(Duration.ofMinutes(16)))).isEqualTo(1);
        CompetitionExtractionJob expired = jobs.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(Status.FAILED);
        assertThat(expired.getErrorCode()).isEqualTo("QUEUE_TIMEOUT");

        assertThat(store.purgeExpired(t0.plus(Duration.ofHours(23)))).isZero();
        assertThat(store.purgeExpired(t0.plus(Duration.ofHours(25)))).isEqualTo(1);
        assertThat(jobs.existsById(id)).isFalse();
    }

    @Test
    void workerPollBeforeRecoveryExpiresOverduePendingWithoutCallingAi() throws Exception {
        // 서버 재시작: scheduledPoll(2초 후)이 scheduledRecovery(30초 후)보다 먼저 돈다. 대기 기한이 지난 작업은 AI로 가면 안 된다.
        Instant now = Instant.now();
        String staleUrl = "https://example.com/stale";
        String freshUrl = "https://example.com/fresh";
        long staleId = store.enqueue(member.getId(), staleUrl, hash(staleUrl), now.minus(Duration.ofMinutes(16))).job().getId();
        long freshId = store.enqueue(member.getId(), freshUrl, hash(freshUrl), now.minusSeconds(1)).job().getId();
        when(extractor.extract(eq(freshUrl), any(Duration.class))).thenReturn(extraction(LocalDate.of(2099, 1, 2)));

        assertThat(worker.pollOnce()).isEqualTo(1);
        awaitTerminal(freshId);

        CompetitionExtractionJob stale = jobs.findById(staleId).orElseThrow();
        assertThat(stale.getStatus()).isEqualTo(Status.FAILED);
        assertThat(stale.getErrorCode()).isEqualTo("QUEUE_TIMEOUT");
        assertThat(stale.getAttemptCount()).isZero();
        assertThat(jobs.findById(freshId).orElseThrow().getStatus()).isEqualTo(Status.SUCCEEDED);
        verify(extractor, never()).extract(eq(staleUrl), any(Duration.class));
        verify(extractor, times(1)).extract(eq(freshUrl), any(Duration.class));
        verify(publicSource, never()).requirePublicAddress(staleUrl);
    }

    private long acceptedJobId(User actor, String url) throws Exception {
        MvcResult result = mvc.perform(json(post(EXTRACTIONS), actor, "{\"url\":\"" + url + "\"}"))
                .andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asLong();
    }

    private void awaitTerminal(long jobId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (jobs.findById(jobId).orElseThrow().isTerminal() && worker.inFlight() == 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("job did not finish: " + jobs.findById(jobId).orElseThrow().getStatus());
    }

    private String hash(String url) {
        return normalizer.normalize(url).hash();
    }

    private static ClaimedJob single(List<ClaimedJob> claimed) {
        assertThat(claimed).hasSize(1);
        return claimed.getFirst();
    }

    private User verified(String key, String universityEmail) {
        User user = User.createOAuthUser(key + "@example.com", key, User.OAuthProvider.KAKAO, key);
        user.verifyUniversityEmail(universityEmail);
        return users.saveAndFlush(user);
    }

    private AiCompetitionExtractClient.Extraction extraction(LocalDate deadline) {
        return new AiCompetitionExtractClient.Extraction("학생 공모전", "기관", "대학생", null,
                LocalDate.of(2099, 1, 1), deadline, null, null, "CONTEST",
                List.of("IT_DEVELOP"), false, "공고 설명", null, null);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User actor, String body) {
        return as(request, actor).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
