package com.giut.server.user.service;

import com.giut.server.global.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local code lifecycle and verification lockout, serialized per account. */
@Component
public class UniversityEmailVerificationCodeStore {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(30);
    private static final Duration LOCKOUT = Duration.ofMinutes(30);
    private static final int MAX_FAILURES = 5;

    private final Map<Long, VerificationState> states = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder encoder;
    private final UniversityEmailSendRateLimiter sendRateLimiter;
    private final Clock clock;

    @Autowired
    public UniversityEmailVerificationCodeStore(PasswordEncoder encoder, UniversityEmailSendRateLimiter sendRateLimiter) {
        this(encoder, sendRateLimiter, Clock.systemDefaultZone());
    }

    UniversityEmailVerificationCodeStore(PasswordEncoder encoder, UniversityEmailSendRateLimiter sendRateLimiter, Clock clock) {
        this.encoder = encoder;
        this.sendRateLimiter = sendRateLimiter;
        this.clock = clock;
    }

    public IssuedCode issue(Long memberId, String email) {
        AtomicReference<IssuedCode> issued = new AtomicReference<>();
        states.compute(memberId, (id, previous) -> {
            Instant now = clock.instant();
            VerificationState state = previous == null ? new VerificationState() : previous;
            state.prune(now);
            requireNotBlocked(state, now);
            sendRateLimiter.acquire(memberId);
            String code = String.format("%06d", random.nextInt(1_000_000));
            Instant expiresAt = now.plus(CODE_TTL);
            state.codes.put(email, new StoredCode(encoder.encode(code), expiresAt));
            issued.set(new IssuedCode(code, LocalDateTime.ofInstant(expiresAt, clock.getZone())));
            return state;
        });
        return issued.get();
    }

    public void verify(Long memberId, String email, String code) {
        AtomicReference<RuntimeException> failure = new AtomicReference<>(
                new IllegalArgumentException("인증코드 요청 내역이 없거나 만료되었습니다."));
        states.computeIfPresent(memberId, (id, state) -> {
            Instant now = clock.instant();
            state.prune(now);
            if (state.blockedUntil != null) {
                failure.set(blocked(now, state.blockedUntil));
            } else {
                StoredCode stored = state.codes.get(email);
                if (stored == null) {
                    failure.set(new IllegalArgumentException("인증코드 요청 내역이 없거나 만료되었습니다."));
                } else if (encoder.matches(code, stored.hash())) {
                    state.codes.remove(email);
                    state.failures.clear();
                    failure.set(null);
                } else {
                    state.failures.addLast(now);
                    if (state.failures.size() >= MAX_FAILURES) {
                        state.blockedUntil = now.plus(LOCKOUT);
                        state.codes.clear();
                        failure.set(blocked(now, state.blockedUntil));
                    } else {
                        failure.set(new IllegalArgumentException("인증코드가 일치하지 않습니다."));
                    }
                }
            }
            return state;
        });
        // Throw outside compute so the fifth failure/lockout is retained even on HTTP/DB rollback.
        if (failure.get() != null) throw failure.get();
    }

    /** Revocation removes codes only; it must not reset an attacker's failure budget or lockout. */
    public void invalidateCodes(Long memberId) {
        states.computeIfPresent(memberId, (id, state) -> {
            state.codes.clear();
            return state;
        });
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void cleanupExpired() {
        states.keySet().forEach(memberId -> states.computeIfPresent(memberId, (id, state) -> {
            state.prune(clock.instant());
            return state.codes.isEmpty() && state.failures.isEmpty() && state.blockedUntil == null ? null : state;
        }));
    }

    private void requireNotBlocked(VerificationState state, Instant now) {
        if (state.blockedUntil != null) throw blocked(now, state.blockedUntil);
    }

    private TooManyRequestsException blocked(Instant now, Instant until) {
        long seconds = Duration.between(now, until).plusNanos(999_999_999).getSeconds();
        return new TooManyRequestsException("인증코드 오답 횟수를 초과했습니다. 30분간 인증과 메일 발송이 제한됩니다.", seconds);
    }

    public record IssuedCode(String code, LocalDateTime expiresAt) {}
    private record StoredCode(String hash, Instant expiresAt) {}

    private static final class VerificationState {
        private final Map<String, StoredCode> codes = new HashMap<>();
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;

        private void prune(Instant now) {
            codes.values().removeIf(code -> !now.isBefore(code.expiresAt()));
            while (!failures.isEmpty() && !failures.peekFirst().isAfter(now.minus(FAILURE_WINDOW))) failures.removeFirst();
            if (blockedUntil != null && !now.isBefore(blockedUntil)) {
                blockedUntil = null;
                failures.clear();
            }
        }
    }
}
