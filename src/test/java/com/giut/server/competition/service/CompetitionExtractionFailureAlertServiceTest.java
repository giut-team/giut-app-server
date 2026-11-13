package com.giut.server.competition.service;

import com.giut.server.competition.event.CompetitionExtractionFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(OutputCaptureExtension.class)
class CompetitionExtractionFailureAlertServiceTest {
    private static final String WEBHOOK = "https://discord.com/api/webhooks/123456/FAKE_TOKEN";
    private static final String URL = "https://www.uos.ac.kr/korNotice/view.do?seq=31723&token=URL_SECRET";

    private final List<String> payloads = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-13T00:00:00Z"));

    @Test
    void sendsOneAlertWithSafeMetadataAndWithoutUrlPathWebhookOrInternalDetail(CapturedOutput output) throws Exception {
        var service = service(WEBHOOK, true, () -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));

        service.onFailed(event(11L));
        awaitFinished(service);

        assertThat(payloads).singleElement().satisfies(payload -> {
            assertThat(payload).contains("추출 최종 실패", "환경: dev", "작업 번호: 11", "오류 코드: AI_UNAVAILABLE",
                    "시도 횟수: 3", "결과를 기다리는 회원: 2명", "공고 도메인: www.uos.ac.kr",
                    "2026-11-13T00:00:00Z", "GET /api/admin/competitions/extraction-failures",
                    "allowed_mentions", "\"parse\":[]");
            assertThat(payload).doesNotContain("FAKE_TOKEN", "URL_SECRET", "korNotice", "AI 서버가 HTTP 503", "@everyone");
        });
        // 로그에는 원인 상세와 URL이 남는다(운영자가 서버 로그에서 검색한다). webhook은 남기지 않는다.
        assertThat(output.getAll()).contains("COMPETITION_EXTRACTION_FAILURE", "jobId=11", "code=AI_UNAVAILABLE",
                        "attempts=3", "waitingUsers=2", "AI 서버가 HTTP 503으로 응답")
                .doesNotContain("FAKE_TOKEN");
    }

    @Test
    void failuresInsideTheCooldownAreCountedAndReportedWithTheNextAlert() throws Exception {
        var service = service(WEBHOOK, true, () -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));

        service.onFailed(event(1L));
        awaitFinished(service);
        clock.advance(Duration.ofSeconds(30));
        service.onFailed(event(2L));
        service.onFailed(event(3L));
        awaitFinished(service);
        assertThat(payloads).hasSize(1);

        clock.advance(Duration.ofSeconds(31)); // 첫 알림 후 61초
        service.onFailed(event(4L));
        awaitFinished(service);
        assertThat(payloads).hasSize(2);
        assertThat(payloads.get(1)).contains("작업 번호: 4", "추가 실패 2건은 묶어서 생략했습니다");
        assertThat(payloads.getFirst()).doesNotContain("묶어서 생략");
    }

    @Test
    void absentMalformedAndDisabledWebhooksNeverSendButFailuresAreStillLogged(CapturedOutput output) throws Exception {
        for (String url : List.of("", " ", "http://discord.com/api/webhooks/123/token",
                "https://evil.invalid/api/webhooks/123/token", WEBHOOK + "?secret=private", WEBHOOK + "#fragment",
                "https://discord.com@evil.invalid/api/webhooks/123/token",
                "https://discord.com/api/webhooks/not-a-number/token")) {
            var service = service(url, true, Mono::never);
            assertThatCode(() -> service.onFailed(event(5L))).doesNotThrowAnyException();
        }
        service(WEBHOOK, false, Mono::never).onFailed(event(6L));

        assertThat(payloads).isEmpty();
        assertThat(output.getAll()).contains("COMPETITION_EXTRACTION_FAILURE jobId=5", "COMPETITION_EXTRACTION_FAILURE jobId=6");
    }

    @Test
    void deliveryFailuresAndRedirectsNeverThrowOrLeakRemoteText(CapturedOutput output) throws Exception {
        for (HttpStatus status : List.of(HttpStatus.FOUND, HttpStatus.BAD_REQUEST, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR)) {
            payloads.clear();
            var service = service(WEBHOOK, true, () -> Mono.just(ClientResponse.create(status)
                    .header("Location", "https://evil.invalid").body("SECRET_REMOTE_BODY").build()));
            assertThatCode(() -> service.onFailed(event(7L))).doesNotThrowAnyException();
            awaitFinished(service);
            assertThat(payloads).hasSize(1);
        }
        var broken = service(WEBHOOK, true, () -> Mono.error(new IllegalStateException(WEBHOOK + "PRIVATE_EXCEPTION")));
        assertThatCode(() -> broken.onFailed(event(8L))).doesNotThrowAnyException();
        awaitFinished(broken);

        assertThat(output.getAll()).contains("알림 전송 실패")
                .doesNotContain("SECRET_REMOTE_BODY", "FAKE_TOKEN", "evil.invalid", "PRIVATE_EXCEPTION");
    }

    @Test
    void sanitizesEnvironmentAndMultilineValues(CapturedOutput output) throws Exception {
        var service = new CompetitionExtractionFailureAlertService(client(() -> Mono.just(
                ClientResponse.create(HttpStatus.NO_CONTENT).build())), WEBHOOK, "@everyone\nSECRET", true, clock);
        service.onFailed(new CompetitionExtractionFailedEvent(9L, "https://example.com/a\nINJECTED", "bad code\n@here",
                "line1\nline2", "d1\r\nd2", 1, 0, clock.instant()));
        awaitFinished(service);

        assertThat(payloads.getFirst()).contains("환경: unknown", "오류 코드: UNKNOWN").doesNotContain("@everyone", "SECRET", "@here");
        assertThat(output.getAll()).contains("message=line1 line2", "detail=d1 d2");
        assertThat(output.getAll().lines().filter(line -> line.contains("COMPETITION_EXTRACTION_FAILURE")).count()).isEqualTo(1);
    }

    private CompetitionExtractionFailureAlertService service(String webhook, boolean enabled,
                                                             Supplier<Mono<ClientResponse>> response) {
        return new CompetitionExtractionFailureAlertService(client(response), webhook, "dev", enabled, clock);
    }

    private WebClient client(Supplier<Mono<ClientResponse>> response) {
        return WebClient.builder().exchangeFunction(request -> {
            var capture = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(capture, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(capture::getBodyAsString)).flatMap(body -> {
                        payloads.add(body);
                        return response.get();
                    });
        }).build();
    }

    private CompetitionExtractionFailedEvent event(Long jobId) {
        return new CompetitionExtractionFailedEvent(jobId, URL, "AI_UNAVAILABLE", "공모전 추출 서비스 응답을 받지 못했습니다.",
                "AI 서버가 HTTP 503으로 응답", 3, 2, clock.instant());
    }

    private static void awaitFinished(CompetitionExtractionFailureAlertService service) throws Exception {
        var slots = (Semaphore) ReflectionTestUtils.getField(service, "deliverySlots");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (slots.availablePermits() != 4 && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(slots.availablePermits()).isEqualTo(4);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
