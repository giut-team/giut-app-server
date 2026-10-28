package com.giut.server.user.service;

import com.giut.server.global.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityEmailSendRateLimiterTest {
    private final MutableClock clock = new MutableClock();
    private final UniversityEmailSendRateLimiter limiter = new UniversityEmailSendRateLimiter(clock);

    @Test
    void permitsFiveSendsAndSixthStartsThirtyMinuteBlock() {
        sendFive(1L);
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1800));
    }

    @Test
    void rollingWindowExpiresEachSendAtItsOwnThirtyMinuteBoundary() {
        limiter.acquire(1L);
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 4; i++) limiter.acquire(1L);
        clock.advance(Duration.ofMinutes(20));
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void repeatedBlockedRequestsDoNotExtendBlockAndExactExpiryAllowsFiveAgain() {
        sendFive(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(29).plusSeconds(59).plusMillis(500));
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1));
        clock.advance(Duration.ofMillis(500));
        sendFive(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void blockStartsOnExcessRequestNotOnFifthSend() {
        sendFive(1L);
        clock.advance(Duration.ofMinutes(20));
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1200));
    }

    @Test
    void unusedQuotaExpiresWithoutStartingBlock() {
        sendFive(1L);
        clock.advance(Duration.ofMinutes(30));
        sendFive(1L);
    }

    @Test
    void otherAccountsHaveIndependentQuotas() {
        sendFive(1L);
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
        sendFive(2L);
    }

    @Test
    void concurrentRequestsPermitExactlyFive() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 12; i++) {
                results.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    try {
                        limiter.acquire(1L);
                        return true;
                    } catch (TooManyRequestsException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int permitted = 0;
            for (var result : results) if (result.get(5, TimeUnit.SECONDS)) permitted++;
            assertThat(permitted).isEqualTo(5);
        }
    }

    @Test
    void nextRequestCleansExpiredHistoriesWithoutDroppingLiveBlock() {
        sendFive(1L);
        limiter.acquire(2L);
        clock.advance(Duration.ofMinutes(20));
        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(10));
        limiter.acquire(3L);
        var histories = (Map<?, ?>) ReflectionTestUtils.getField(limiter, "histories");
        assertThat(histories.keySet()).hasSize(2);
        assertThat(histories.containsKey(1L)).isTrue();
        assertThat(histories.containsKey(2L)).isFalse();
        clock.advance(Duration.ofMinutes(20));
        limiter.acquire(4L);
        assertThat(histories.containsKey(1L)).isFalse();
    }

    private void sendFive(Long memberId) {
        for (int i = 0; i < 5; i++) limiter.acquire(memberId);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-27T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
