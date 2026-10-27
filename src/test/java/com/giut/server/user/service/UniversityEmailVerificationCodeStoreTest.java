package com.giut.server.user.service;

import com.giut.server.global.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UniversityEmailVerificationCodeStoreTest {
    private static final String EMAIL = "student@uos.ac.kr";
    private final MutableClock clock = new MutableClock();
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UniversityEmailVerificationCodeStore store = new UniversityEmailVerificationCodeStore(
            encoder, new UniversityEmailSendRateLimiter(clock), clock);

    @BeforeEach
    void setup() {
        when(encoder.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
        when(encoder.matches(anyString(), anyString())).thenAnswer(call ->
                ("hash:" + call.getArgument(0)).equals(call.getArgument(1)));
    }

    @Test
    void springRegistersOneMinuteCleanupAndItsActualRunnableRemovesExpiredCodes() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(com.giut.server.user.config.UniversityEmailVerificationSchedulingConfig.class);
            context.registerBean(UniversityEmailVerificationCodeStore.class, () -> store);
            context.refresh();
            var processor = context.getBean(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).hasSize(1);
            var cleanup = processor.getScheduledTasks().iterator().next();
            assertThat(cleanup.getTask()).isInstanceOf(org.springframework.scheduling.config.FixedDelayTask.class);
            var interval = (org.springframework.scheduling.config.FixedDelayTask) cleanup.getTask();
            assertThat(interval.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1));
            assertThat(interval.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(1));
            store.issue(1L, EMAIL);
            clock.advance(Duration.ofMinutes(5));
            cleanup.getTask().getRunnable().run();
            assertThat(states()).isEmpty();
        }
    }

    @Test
    void validCodeWorksExactlyOnce() {
        var issued = store.issue(1L, EMAIL);
        store.verify(1L, EMAIL, issued.code());
        assertThatThrownBy(() -> store.verify(1L, EMAIL, issued.code())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fourWrongAnswersStillAllowCorrectCodeAndSuccessResetsFailureCount() {
        var first = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, first.code());
        store.verify(1L, EMAIL, first.code());
        var second = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, second.code());
        assertThatCode(() -> store.verify(1L, EMAIL, second.code())).doesNotThrowAnyException();
    }

    @Test
    void fifthWrongAnswerInvalidatesAllCodesAndBlocksVerificationAndSending() {
        var first = store.issue(1L, EMAIL);
        var second = store.issue(1L, "other@uos.ac.kr");
        wrongFour(1L, EMAIL, first.code());
        assertThatThrownBy(() -> store.verify(1L, EMAIL, wrong(first.code())))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e ->
                        assertThat(e.getRetryAfterSeconds()).isEqualTo(1800));
        assertThatThrownBy(() -> store.verify(1L, "other@uos.ac.kr", second.code()))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> store.issue(1L, "changed@uos.ac.kr"))
                .isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(30));
        assertThatThrownBy(() -> store.verify(1L, EMAIL, first.code())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.verify(1L, "other@uos.ac.kr", second.code()))
                .isInstanceOf(IllegalArgumentException.class);
        var fresh = store.issue(1L, EMAIL);
        store.verify(1L, EMAIL, fresh.code());
    }

    @Test
    void resendingAndChangingEmailDoNotResetFailures() {
        var first = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, first.code());
        store.issue(1L, EMAIL);
        var changed = store.issue(1L, "changed@uos.ac.kr");
        assertThatThrownBy(() -> store.verify(1L, "changed@uos.ac.kr", wrong(changed.code())))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void repeatedBlockedRequestsAndRevocationDoNotExtendOrClearLockout() {
        var issued = store.issue(1L, EMAIL);
        block(1L, EMAIL, issued.code());
        store.invalidateCodes(1L);
        clock.advance(Duration.ofMinutes(29).plusSeconds(59).plusMillis(500));
        store.cleanupExpired();
        assertThatThrownBy(() -> store.issue(1L, EMAIL))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1));
        assertThatThrownBy(() -> store.verify(1L, EMAIL, issued.code()))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1));
        clock.advance(Duration.ofMillis(500));
        assertThatCode(() -> store.issue(1L, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void revocationBeforeFifthWrongAnswerDoesNotResetFailureBudget() {
        var first = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, first.code());
        store.invalidateCodes(1L);
        var fresh = store.issue(1L, EMAIL);
        assertThatThrownBy(() -> store.verify(1L, EMAIL, wrong(fresh.code())))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void otherAccountsAreNotBlocked() {
        var first = store.issue(1L, EMAIL);
        block(1L, EMAIL, first.code());
        var other = store.issue(2L, EMAIL);
        store.verify(2L, EMAIL, other.code());
    }

    @Test
    void missingAndExpiredCodesDoNotCountAsWrongGuesses() {
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> store.verify(1L, EMAIL, "000000")).isInstanceOf(IllegalArgumentException.class);
        }
        var expired = store.issue(1L, EMAIL);
        clock.advance(Duration.ofMinutes(5));
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> store.verify(1L, EMAIL, expired.code())).isInstanceOf(IllegalArgumentException.class);
        }
        var fresh = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, fresh.code());
        store.verify(1L, EMAIL, fresh.code());
    }

    @Test
    void wrongGuessesForAnotherEmailDoNotInvalidateRealCode() {
        var issued = store.issue(1L, EMAIL);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> store.verify(1L, "missing@uos.ac.kr", "000000"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        store.verify(1L, EMAIL, issued.code());
    }

    @Test
    void oldFailuresExpireAfterThirtyMinutesWithoutStartingLockout() {
        var first = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, first.code());
        clock.advance(Duration.ofMinutes(30));
        store.cleanupExpired();
        var fresh = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, fresh.code());
        store.verify(1L, EMAIL, fresh.code());
    }

    @Test
    void cleanupRemovesExpiredCodesEvenWhenNobodyVerifiesAgain() {
        store.issue(1L, EMAIL);
        clock.advance(Duration.ofMinutes(4));
        var later = store.issue(2L, EMAIL);
        clock.advance(Duration.ofMinutes(1));
        store.cleanupExpired();
        assertThat(states().containsKey(1L)).isFalse();
        assertThat(states().containsKey(2L)).isTrue();
        store.verify(2L, EMAIL, later.code());
        store.cleanupExpired();
        assertThat(states()).isEmpty();
    }

    @Test
    void cleanupOfExpiredCodeKeepsFailureBudgetForNewCode() {
        var expired = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, expired.code());
        clock.advance(Duration.ofMinutes(5));
        store.cleanupExpired();
        var fresh = store.issue(1L, EMAIL);
        assertThatThrownBy(() -> store.verify(1L, EMAIL, wrong(fresh.code())))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void cleanupRemovesExpiredLockoutButDoesNotResetSendQuotaPrematurely() {
        var issued = store.issue(1L, EMAIL);
        block(1L, EMAIL, issued.code());
        clock.advance(Duration.ofMinutes(30));
        store.cleanupExpired();
        assertThat(states()).isEmpty();
        for (int i = 0; i < 5; i++) store.issue(1L, EMAIL);
        assertThatThrownBy(() -> store.issue(1L, EMAIL)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void concurrentWrongAnswersUseOnlyFiveHashChecksAndThenBlock() throws Exception {
        var issued = store.issue(1L, EMAIL);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 12; i++) results.add(executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                return verifyStatus(1L, EMAIL, wrong(issued.code()));
            }));
            start.countDown();
            int ordinaryWrong = 0;
            int blocked = 0;
            for (var result : results) {
                int status = result.get(5, TimeUnit.SECONDS);
                if (status == 400) ordinaryWrong++;
                if (status == 429) blocked++;
            }
            assertThat(ordinaryWrong).isEqualTo(4);
            assertThat(blocked).isEqualTo(8);
            verify(encoder, times(5)).matches(anyString(), anyString());
        }
    }

    @Test
    void concurrentCorrectRequestsConsumeCodeOnlyOnce() throws Exception {
        var issued = store.issue(1L, EMAIL);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> verifyStatus(1L, EMAIL, issued.code()));
            var second = executor.submit(() -> verifyStatus(1L, EMAIL, issued.code()));
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
    }

    @Test
    void resendWaitingForFifthWrongAnswerCannotIssueCodeAfterLockout() throws Exception {
        var issued = store.issue(1L, EMAIL);
        wrongFour(1L, EMAIL, issued.code());
        CountDownLatch matching = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        doAnswer(call -> {
            matching.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
            return false;
        }).when(encoder).matches(anyString(), anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var verify = executor.submit(() -> verifyStatus(1L, EMAIL, wrong(issued.code())));
            try {
                assertThat(matching.await(5, TimeUnit.SECONDS)).isTrue();
                var resend = executor.submit(() -> {
                    try { store.issue(1L, EMAIL); return 200; }
                    catch (TooManyRequestsException e) { return 429; }
                });
                assertThatThrownBy(() -> resend.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                finish.countDown();
                assertThat(verify.get(5, TimeUnit.SECONDS)).isEqualTo(429);
                assertThat(resend.get(5, TimeUnit.SECONDS)).isEqualTo(429);
            } finally { finish.countDown(); }
        }
        verify(encoder, times(1)).encode(anyString());
    }

    @Test
    void cleanupCannotDeleteFreshReplacementWhileIssueIsInProgress() throws Exception {
        store.issue(1L, EMAIL);
        clock.advance(Duration.ofMinutes(5));
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        doAnswer(call -> {
            encoding.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
            return "hash:" + call.getArgument(0);
        }).when(encoder).encode(anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var issue = executor.submit(() -> store.issue(1L, EMAIL));
            try {
                assertThat(encoding.await(5, TimeUnit.SECONDS)).isTrue();
                var cleanup = executor.submit(store::cleanupExpired);
                assertThatThrownBy(() -> cleanup.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                finish.countDown();
                var fresh = issue.get(5, TimeUnit.SECONDS);
                cleanup.get(5, TimeUnit.SECONDS);
                store.verify(1L, EMAIL, fresh.code());
            } finally { finish.countDown(); }
        }
    }

    private int verifyStatus(Long memberId, String email, String code) {
        try { store.verify(memberId, email, code); return 200; }
        catch (TooManyRequestsException e) { return 429; }
        catch (IllegalArgumentException e) { return 400; }
    }

    private void wrongFour(Long userId, String email, String correctCode) {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> store.verify(userId, email, wrong(correctCode)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void block(Long userId, String email, String correctCode) {
        wrongFour(userId, email, correctCode);
        assertThatThrownBy(() -> store.verify(userId, email, wrong(correctCode)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    private String wrong(String correct) { return "000000".equals(correct) ? "000001" : "000000"; }
    private Map<?, ?> states() { return (Map<?, ?>) ReflectionTestUtils.getField(store, "states"); }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-28T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
