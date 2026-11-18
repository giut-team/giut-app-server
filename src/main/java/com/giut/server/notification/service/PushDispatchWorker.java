package com.giut.server.notification.service;

import com.giut.server.notification.config.PushProperties;
import com.giut.server.notification.push.PushGateway;
import com.giut.server.notification.push.PushGateway.PushReceipt;
import com.giut.server.notification.push.PushGateway.PushTicket;
import com.giut.server.notification.push.PushGatewayException;
import com.giut.server.notification.service.PushDeliveryStore.ClaimedPush;
import com.giut.server.notification.service.PushDeliveryStore.ReceiptCheck;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DB outbox 기반 푸시 발송 작업자.
 *
 * <p>발송·영수증 조회(외부 HTTP)는 전용 스레드 1개에서 DB 트랜잭션 밖으로 실행한다. 공용 Spring scheduler 스레드가
 * Expo 응답을 기다리느라 다른 예약 작업(추출 작업 선점 등)을 막지 않게 하기 위해서다. 같은 일이 이미 실행 중이면 새로 넣지 않으므로
 * 메모리 대기열이 쌓이지 않는다. 회수·정리는 DB만 쓰므로 scheduler 스레드에서 바로 실행한다.
 *
 * <p>전달 보장은 "최소 한 번"이다. Expo가 받은 뒤 결과를 저장하기 전에 서버가 멈추면 임대 만료 회수가 같은 푸시를 다시 보낼 수 있다.
 */
@Component
public class PushDispatchWorker {
    /** 서버 로그에서 운영자 조치가 필요한 자격 증명 오류만 찾을 때 쓰는 고정 접두어. */
    public static final String CONFIGURATION_ERROR_LOG_PREFIX = "PUSH_CONFIGURATION_ERROR";
    private static final Logger log = LoggerFactory.getLogger(PushDispatchWorker.class);
    private static final int MAX_BATCHES_PER_TICK = 10;

    private final PushDeliveryStore store;
    private final PushGateway gateway;
    private final PushProperties properties;
    private final Clock clock;
    private final ExecutorService executor;
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private final AtomicBoolean checkingReceipts = new AtomicBoolean();
    private volatile boolean running = true;

    public PushDispatchWorker(PushDeliveryStore store, PushGateway gateway, PushProperties properties, Clock clock) {
        this.store = store;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "push-dispatch");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(initialDelayString = "${app.push.poll-interval:2s}", fixedDelayString = "${app.push.poll-interval:2s}")
    void scheduledDispatch() {
        if (properties.workerEnabled()) {
            submitOnce(dispatching, this::drain);
        }
    }

    @Scheduled(initialDelayString = "1m", fixedDelayString = "1m")
    void scheduledReceipts() {
        if (properties.workerEnabled()) {
            submitOnce(checkingReceipts, this::checkReceiptsOnce);
        }
    }

    @Scheduled(initialDelayString = "30s", fixedDelayString = "30s")
    void scheduledRecovery() {
        if (properties.workerEnabled()) {
            recoverOnce();
        }
    }

    @Scheduled(initialDelayString = "5m", fixedDelayString = "1h")
    void scheduledCleanup() {
        if (properties.workerEnabled()) {
            cleanupOnce();
        }
    }

    /**
     * 보낼 차례인 푸시를 한 묶음(최대 batchSize) 선점해 Expo로 보내고 결과를 저장한다.
     *
     * @return 이번에 선점한 수
     */
    public int dispatchOnce() {
        List<ClaimedPush> claimed;
        try {
            claimed = store.claim(properties.batchSize(), clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Push delivery claim failed", exception);
            return 0;
        }
        if (claimed.isEmpty()) {
            return 0;
        }
        List<PushTicket> tickets;
        try {
            tickets = gateway.send(claimed.stream().map(ClaimedPush::message).toList());
        } catch (PushGatewayException exception) {
            log.warn("PUSH_SEND_REQUEST_FAILED count={} code={} retryable={} message={}",
                    claimed.size(), exception.code(), exception.retryable(), exception.getMessage());
            try {
                store.recordRequestFailure(claimed, exception, clock.instant());
            } catch (RuntimeException saveFailure) {
                log.warn("Saving push request failure failed; lease recovery will retry", saveFailure);
            }
            return claimed.size();
        } catch (RuntimeException exception) {
            log.error("Push send failed unexpectedly; lease recovery will retry {} deliveries", claimed.size(), exception);
            return claimed.size();
        }
        try {
            int configurationErrors = store.recordTickets(claimed, tickets, clock.instant());
            if (configurationErrors > 0) {
                log.error("{} source=ticket count={} (Expo/EAS 푸시 자격 증명을 확인하세요)",
                        CONFIGURATION_ERROR_LOG_PREFIX, configurationErrors);
            }
        } catch (RuntimeException exception) {
            // Expo는 받았지만 결과를 저장하지 못했다. 임대 만료 뒤 다시 보내므로 같은 푸시가 한 번 더 갈 수 있다.
            log.warn("Saving push tickets failed; lease recovery will resend {} deliveries", claimed.size(), exception);
        }
        return claimed.size();
    }

    /** @return 이번에 확인한 영수증 대상 수 */
    public int checkReceiptsOnce() {
        List<ReceiptCheck> due;
        try {
            due = store.findReceiptDue(clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Finding push receipts failed", exception);
            return 0;
        }
        if (due.isEmpty()) {
            return 0;
        }
        Map<String, PushReceipt> receipts;
        try {
            receipts = gateway.receipts(due.stream().map(ReceiptCheck::ticketId).toList());
        } catch (RuntimeException exception) {
            // 다음 주기에 같은 대상을 다시 확인한다.
            log.warn("Push receipt request failed for {} tickets", due.size(), exception);
            return 0;
        }
        try {
            int configurationErrors = store.recordReceipts(due, receipts, clock.instant());
            if (configurationErrors > 0) {
                log.error("{} source=receipt count={} (Expo/EAS 푸시 자격 증명을 확인하세요)",
                        CONFIGURATION_ERROR_LOG_PREFIX, configurationErrors);
            }
        } catch (RuntimeException exception) {
            log.warn("Saving push receipts failed", exception);
        }
        return due.size();
    }

    public void recoverOnce() {
        try {
            int recovered = store.recoverExpiredLeases(clock.instant());
            int expired = store.expireStalePending(clock.instant());
            if (recovered > 0 || expired > 0) {
                log.warn("Push delivery recovery: {} expired leases, {} stale pending", recovered, expired);
            }
        } catch (RuntimeException exception) {
            log.warn("Push delivery recovery failed", exception);
        }
    }

    public void cleanupOnce() {
        try {
            int purged = store.purgeCompleted(clock.instant());
            int stale = store.deactivateStaleDevices(clock.instant());
            if (purged > 0 || stale > 0) {
                log.info("Push cleanup: {} old deliveries purged, {} stale devices deactivated", purged, stale);
            }
        } catch (RuntimeException exception) {
            log.warn("Push cleanup failed", exception);
        }
    }

    private void drain() {
        for (int batch = 0; batch < MAX_BATCHES_PER_TICK && running; batch++) {
            if (dispatchOnce() < properties.batchSize()) {
                return;
            }
        }
    }

    private void submitOnce(AtomicBoolean busy, Runnable task) {
        if (!running || !busy.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    busy.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            busy.set(false);
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
}
