package com.giut.server.profile.service;

import com.giut.server.profile.entity.ProfileReport;
import com.giut.server.profile.event.ProfileReportCreatedEvent;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ProfileReportAlertServiceTest {
    static final String WEBHOOK = "https://discord.com/api/webhooks/123456/FAKE_TOKEN";
    private final List<String> payloads = new CopyOnWriteArrayList<>();

    @Test
    void sendsEveryReportWithSafeMetadataAndWithoutTheErrorAlertCooldown() throws Exception {
        var service = service(() -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()));
        for (ProfileReport.Reason reason : ProfileReport.Reason.values()) {
            service.onReportCreated(event(reason));
            awaitFinished(service);
        }
        assertThat(payloads).hasSize(4);
        assertThat(payloads.getFirst()).contains("신고 접수 알림", "신고 번호: 10", "스팸·광고", "PENDING",
                        "2026-10-27T01:00:00Z", "/api/admin/profile-reports/10", "allowed_mentions", "\"parse\":[]")
                .doesNotContain("FAKE_TOKEN", "reporter", "description", "snapshot", "@everyone");
        assertThat(payloads.get(1)).contains("허위 정보·사칭");
        assertThat(payloads.get(2)).contains("부적절한 행동");
        assertThat(payloads.get(3)).contains("기타");
    }

    @Test
    void absentMalformedAndDisabledWebhooksNeverSend() throws Exception {
        for (String url : List.of("", " ", "http://discord.com/api/webhooks/123/token",
                "https://evil.invalid/api/webhooks/123/token", WEBHOOK + "?secret=private",
                WEBHOOK + "#fragment", "https://discord.com@evil.invalid/api/webhooks/123/token",
                "https://discord.com/api/webhooks/not-a-number/token")) {
            var service = new ProfileReportAlertService(client(Mono::never), url, "dev", true);
            service.onReportCreated(event(ProfileReport.Reason.OTHER));
            awaitFinished(service);
        }
        new ProfileReportAlertService(client(Mono::never), WEBHOOK, "dev", false)
                .onReportCreated(event(ProfileReport.Reason.OTHER));
        assertThat(payloads).isEmpty();
    }

    @Test
    void sanitizesEnvironmentAndDoesNotUseUserSuppliedText() throws Exception {
        var service = new ProfileReportAlertService(client(() -> Mono.just(
                ClientResponse.create(HttpStatus.NO_CONTENT).build())), WEBHOOK, "@everyone\nSECRET", true);
        service.onReportCreated(event(ProfileReport.Reason.OTHER));
        awaitFinished(service);
        assertThat(payloads.getFirst()).contains("환경: unknown").doesNotContain("@everyone", "SECRET");
    }

    @Test
    void failuresAndRedirectsReleaseSlotsWithoutThrowingOrAutomaticRetry(CapturedOutput output) throws Exception {
        for (HttpStatus status : List.of(HttpStatus.FOUND, HttpStatus.BAD_REQUEST,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR)) {
            payloads.clear();
            var service = service(() -> Mono.just(ClientResponse.create(status)
                    .header("Location", "https://evil.invalid").body("SECRET_REMOTE_BODY").build()));
            assertThatCode(() -> service.onReportCreated(event(ProfileReport.Reason.OTHER)))
                    .doesNotThrowAnyException();
            awaitFinished(service);
            assertThat(payloads).hasSize(1);
            assertThat(payloads.getFirst()).doesNotContain("SECRET_REMOTE_BODY", "evil.invalid");
            service.onReportCreated(event(ProfileReport.Reason.OTHER));
            awaitFinished(service);
            assertThat(payloads).hasSize(2);
        }
        assertThat(output.getAll()).doesNotContain("SECRET_REMOTE_BODY", "FAKE_TOKEN", "evil.invalid");
    }

    @Test
    void synchronousTransportSetupFailureIsContained(CapturedOutput output) throws Exception {
        WebClient brokenClient = org.mockito.Mockito.mock(WebClient.class);
        org.mockito.Mockito.when(brokenClient.post()).thenThrow(new IllegalStateException("SECRET_URL"));
        var service = new ProfileReportAlertService(brokenClient, WEBHOOK, "dev", true);
        assertThatCode(() -> service.onReportCreated(event(ProfileReport.Reason.OTHER))).doesNotThrowAnyException();
        awaitFinished(service);
        assertThat(output.getAll()).doesNotContain("SECRET_URL", "FAKE_TOKEN");
    }

    @Test
    void networkExceptionDoesNotLeakWebhookOrExceptionText(CapturedOutput output) throws Exception {
        var service = service(() -> Mono.error(new IllegalStateException(WEBHOOK + "PRIVATE_EXCEPTION")));
        service.onReportCreated(event(ProfileReport.Reason.OTHER));
        awaitFinished(service);
        assertThat(output.getAll()).contains("신고 알림 전송 실패")
                .doesNotContain("FAKE_TOKEN", "PRIVATE_EXCEPTION", WEBHOOK);
    }

    @Test
    void timeoutReleasesSlotWithoutWaitingInTheCaller() throws Exception {
        var service = service(Mono::never);
        try (var caller = Executors.newSingleThreadExecutor()) {
            caller.submit(() -> service.onReportCreated(event(ProfileReport.Reason.OTHER)))
                    .get(1, TimeUnit.SECONDS);
        }
        awaitFinished(service);
        assertThat(payloads).hasSize(1);
    }

    @Test
    void outstandingWorkIsBoundedAndReleasedAfterCompletion() throws Exception {
        var entered = new CountDownLatch(8);
        var release = reactor.core.publisher.Sinks.<Boolean>one();
        var service = service(() -> {
            entered.countDown();
            return release.asMono().map(ignored -> ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });
        try {
            for (int i = 0; i < 8; i++) service.onReportCreated(event(ProfileReport.Reason.OTHER));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            service.onReportCreated(event(ProfileReport.Reason.OTHER));
            assertThat(payloads).hasSize(8);
        } finally {
            release.tryEmitValue(true);
        }
        awaitFinished(service);
        service.onReportCreated(event(ProfileReport.Reason.OTHER));
        awaitFinished(service);
        assertThat(payloads).hasSize(9);
    }

    private ProfileReportAlertService service(Supplier<Mono<ClientResponse>> response) {
        return new ProfileReportAlertService(client(response), WEBHOOK, "dev", true);
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

    static ProfileReportCreatedEvent event(ProfileReport.Reason reason) {
        return new ProfileReportCreatedEvent(10L, reason, Instant.parse("2026-10-27T01:00:00Z"));
    }

    static void awaitFinished(ProfileReportAlertService service) throws Exception {
        var slots = (Semaphore) ReflectionTestUtils.getField(service, "deliverySlots");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (slots.availablePermits() != 8 && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(slots.availablePermits()).isEqualTo(8);
    }
}
