package com.giut.server.user.service;

import com.giut.server.global.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Process-local, per-account quota, just like the current verification-code storage. */
@Component
public class UniversityEmailSendRateLimiter {
    private static final int MAX_SENDS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(30);
    private static final Duration BLOCK = Duration.ofMinutes(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<Long, SendHistory> histories = new HashMap<>();
    private Instant nextCleanupAt = Instant.MIN;

    public UniversityEmailSendRateLimiter() {
        this(Clock.systemUTC());
    }

    UniversityEmailSendRateLimiter(Clock clock) {
        this.clock = clock;
    }

    // Reserve before hashing/SMTP; concurrent requests share the same five slots.
    // Failed delivery attempts still consume a slot to avoid unbounded SMTP retries.
    public synchronized void acquire(Long memberId) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanupAt)) {
            histories.values().removeIf(history -> !now.isBefore(history.expiresAt()));
            nextCleanupAt = now.plus(CLEANUP_INTERVAL);
        }

        SendHistory history = histories.computeIfAbsent(memberId, ignored -> new SendHistory());
        if (history.blockedUntil != null) {
            if (now.isBefore(history.blockedUntil)) {
                throw blocked(now, history.blockedUntil);
            }
            history.blockedUntil = null;
            history.sends.clear();
        }

        Instant cutoff = now.minus(WINDOW);
        while (!history.sends.isEmpty() && !history.sends.peekFirst().isAfter(cutoff)) {
            history.sends.removeFirst();
        }
        if (history.sends.size() >= MAX_SENDS) {
            history.blockedUntil = now.plus(BLOCK);
            throw blocked(now, history.blockedUntil);
        }
        history.sends.addLast(now);
    }

    private TooManyRequestsException blocked(Instant now, Instant blockedUntil) {
        long retryAfterSeconds = Duration.between(now, blockedUntil).plusNanos(999_999_999).getSeconds();
        return new TooManyRequestsException(
                "학교 인증 메일 발송 횟수를 초과했습니다. 잠시 후 다시 요청해 주세요.", retryAfterSeconds);
    }

    private static final class SendHistory {
        private final Deque<Instant> sends = new ArrayDeque<>();
        private Instant blockedUntil;

        private Instant expiresAt() {
            return blockedUntil != null ? blockedUntil : sends.getLast().plus(WINDOW);
        }
    }
}
