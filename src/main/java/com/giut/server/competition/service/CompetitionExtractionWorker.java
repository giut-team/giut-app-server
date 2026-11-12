package com.giut.server.competition.service;

import com.giut.server.competition.config.CompetitionExtractionProperties;
import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.service.AiCompetitionExtractClient.ExtractionUnavailable;
import com.giut.server.competition.service.CompetitionExtractionJobStore.ClaimedJob;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.competition.util.PublicCompetitionSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DB 기반 회원 URL 추출 작업자.
 *
 * <p>공용 Spring scheduler는 짧은 선점·회수·정리 트랜잭션만 실행한다. AI 호출은 {@code concurrency}개 고정 전용 스레드에서
 * DB 트랜잭션 밖으로 실행한다. 빈 실행 슬롯 수만큼만 선점하므로 메모리 대기열이 생기지 않고, 선점하지 못한 작업은 DB에 PENDING으로 남는다.
 */
@Component
public class CompetitionExtractionWorker {
    private static final Logger log = LoggerFactory.getLogger(CompetitionExtractionWorker.class);

    private final CompetitionExtractionJobStore store;
    private final AiCompetitionExtractClient extractor;
    private final CompetitionUrlNormalizer urlNormalizer;
    private final PublicCompetitionSource publicSource;
    private final ObjectMapper objectMapper;
    private final CompetitionExtractionProperties properties;
    private final Semaphore slots;
    private final ExecutorService executor;
    private volatile boolean running = true;

    public CompetitionExtractionWorker(CompetitionExtractionJobStore store, AiCompetitionExtractClient extractor,
                                       CompetitionUrlNormalizer urlNormalizer, PublicCompetitionSource publicSource,
                                       ObjectMapper objectMapper, CompetitionExtractionProperties properties) {
        this.store = store;
        this.extractor = extractor;
        this.urlNormalizer = urlNormalizer;
        this.publicSource = publicSource;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.slots = new Semaphore(properties.concurrency());
        this.executor = Executors.newFixedThreadPool(properties.concurrency(), threadFactory());
    }

    @Scheduled(initialDelayString = "${app.ai-extraction.jobs.poll-interval:2s}",
            fixedDelayString = "${app.ai-extraction.jobs.poll-interval:2s}")
    void scheduledPoll() {
        if (properties.workerEnabled()) {
            pollOnce();
        }
    }

    @Scheduled(initialDelayString = "30s", fixedDelayString = "30s")
    void scheduledRecovery() {
        if (properties.workerEnabled()) {
            recoverOnce();
        }
    }

    @Scheduled(initialDelayString = "5m", fixedDelayString = "10m")
    void scheduledCleanup() {
        if (properties.workerEnabled()) {
            try {
                int purged = store.purgeExpired(Instant.now());
                if (purged > 0) log.info("Purged {} expired competition extraction jobs", purged);
            } catch (RuntimeException exception) {
                log.warn("Competition extraction cleanup failed", exception);
            }
        }
    }

    /** 빈 실행 슬롯만큼 선점해 전용 스레드에 넘긴다. 반환값은 이번에 시작한 작업 수. */
    public int pollOnce() {
        if (!running) {
            return 0;
        }
        int available = slots.availablePermits();
        if (available == 0 || !slots.tryAcquire(available)) {
            return 0;
        }
        List<ClaimedJob> claimed;
        try {
            claimed = store.claim(available, Instant.now());
        } catch (RuntimeException exception) {
            slots.release(available);
            log.warn("Competition extraction claim failed", exception);
            return 0;
        }
        slots.release(available - claimed.size());
        for (ClaimedJob job : claimed) {
            try {
                executor.execute(() -> {
                    try {
                        execute(job);
                    } finally {
                        slots.release();
                    }
                });
            } catch (RejectedExecutionException exception) {
                // 종료 중이다. 이 작업은 RUNNING으로 남고 임대 만료 회수가 다시 대기시킨다.
                slots.release();
            }
        }
        return claimed.size();
    }

    public void recoverOnce() {
        try {
            Instant now = Instant.now();
            int recovered = store.recoverExpiredLeases(now);
            int expired = store.expireStalePending(now);
            if (recovered > 0 || expired > 0) {
                log.warn("Competition extraction recovery: {} expired leases, {} stale pending jobs", recovered, expired);
            }
        } catch (RuntimeException exception) {
            log.warn("Competition extraction recovery failed", exception);
        }
    }

    /** 실행 중인 AI 호출 수(테스트·관측용). */
    public int inFlight() {
        return properties.concurrency() - slots.availablePermits();
    }

    /** DB 트랜잭션 밖에서 실행한다. 결과·실패 저장만 각각 짧은 트랜잭션으로 store에 위임한다. */
    void execute(ClaimedJob job) {
        String resultJson;
        try {
            publicSource.requirePublicAddress(urlNormalizer.normalize(job.sourceUrl()).value());
            AiCompetitionExtractClient.Extraction extraction = extractor.extract(job.sourceUrl(), properties.callTimeout());
            resultJson = objectMapper.writeValueAsString(extraction);
        } catch (ExtractionUnavailable exception) {
            fail(job, exception.status() == HttpStatus.GATEWAY_TIMEOUT ? "AI_TIMEOUT"
                    : exception.status() == HttpStatus.SERVICE_UNAVAILABLE ? "AI_NOT_CONFIGURED"
                    : exception.retryable() ? "AI_UNAVAILABLE" : "AI_REJECTED",
                    exception.getMessage(), exception.retryable());
            return;
        } catch (IllegalArgumentException exception) {
            // 실행 시점 DNS 재확인 실패(사설 주소로 바뀐 경우 포함). 같은 입력으로 재시도하지 않는다.
            fail(job, "INVALID_SOURCE", "공개 인터넷 공고 URL만 추출할 수 있습니다.", false);
            return;
        } catch (RuntimeException exception) {
            if (!running) {
                log.info("Competition extraction job {} interrupted by shutdown; lease recovery will retry it", job.id());
                return;
            }
            log.error("Competition extraction job {} failed unexpectedly", job.id(), exception);
            fail(job, "INTERNAL_ERROR", "공모전 정보를 추출하지 못했습니다.", false);
            return;
        }

        if (resultJson.length() > CompetitionExtractionJob.MAX_RESULT_LENGTH) {
            fail(job, "INVALID_RESULT", "추출 결과가 너무 커서 저장할 수 없습니다.", false);
            return;
        }
        try {
            if (!store.recordSuccess(job, resultJson, Instant.now())) {
                log.info("Discarded stale extraction result for job {} (lease taken over)", job.id());
            }
        } catch (RuntimeException exception) {
            // AI 결과를 받았지만 저장하지 못했다. 임대 만료 후 재시도되며 그때 AI가 다시 호출될 수 있다.
            log.warn("Saving extraction result for job {} failed", job.id(), exception);
        }
    }

    private void fail(ClaimedJob job, String code, String message, boolean retryable) {
        if (!running) {
            return;
        }
        try {
            if (!store.recordFailure(job, code, message, retryable, Instant.now())) {
                log.info("Discarded stale extraction failure for job {} (lease taken over)", job.id());
            }
        } catch (RuntimeException exception) {
            log.warn("Saving extraction failure for job {} failed", job.id(), exception);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        running = false;
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "competition-extraction-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
