package com.giut.server.competition.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class AiCompetitionExtractClient {
    /** 동기 미리보기(deprecated extract-preview)의 기존 제한 시간. 사용자 HTTP 요청을 오래 붙잡지 않도록 유지한다. */
    public static final Duration PREVIEW_TIMEOUT = Duration.ofSeconds(20);

    private final WebClient webClient;
    private final String endpoint;

    public AiCompetitionExtractClient(WebClient webClient, @Value("${app.ai-extraction.url:}") String endpoint) {
        this.webClient = webClient;
        this.endpoint = endpoint;
    }

    public boolean isConfigured() {
        return !endpoint.isBlank();
    }

    public Extraction extract(String sourceUrl) {
        return extract(sourceUrl, PREVIEW_TIMEOUT);
    }

    /**
     * 호출 스레드를 막고 기다린다. 비동기 작업자는 DB 트랜잭션 밖의 전용 스레드에서만 호출한다.
     * 실패의 HTTP 상태는 기존 미리보기 계약(504/502/503)을 유지하고, 재시도 가능 여부는 {@link ExtractionUnavailable#retryable()}로 구분한다.
     */
    public Extraction extract(String sourceUrl, Duration timeout) {
        if (!isConfigured()) {
            throw new ExtractionUnavailable(HttpStatus.SERVICE_UNAVAILABLE, "공모전 추출 서비스가 설정되지 않았습니다.", false);
        }
        return webClient.post().uri(endpoint)
                .bodyValue(Map.of("url", sourceUrl))
                .retrieve()
                .bodyToMono(Extraction.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Empty extraction response")))
                .timeout(timeout)
                .onErrorMap(error -> !(error instanceof ExtractionUnavailable), AiCompetitionExtractClient::classify)
                .block();
    }

    private static ExtractionUnavailable classify(Throwable error) {
        if (error instanceof TimeoutException) {
            return new ExtractionUnavailable(HttpStatus.GATEWAY_TIMEOUT, "공모전 추출 응답 시간이 초과됐습니다.", true);
        }
        // 연결 실패, AI 서버 과부하(5xx·429·408)는 잠시 뒤 같은 요청이 성공할 수 있다.
        // 그 밖의 4xx·응답 형식 오류는 같은 입력으로 재시도해도 같은 결과일 가능성이 높아 재시도하지 않는다.
        boolean retryable = error instanceof WebClientRequestException
                || error instanceof WebClientResponseException response
                && (response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429
                || response.getStatusCode().value() == 408);
        return new ExtractionUnavailable(HttpStatus.BAD_GATEWAY, "공모전 추출 서비스 응답을 받지 못했습니다.", retryable);
    }

    public record Extraction(
            String title, String hostOrganization, String targetParticipants, Boolean isForEnrolledOnly,
            LocalDate applicationStartAt, LocalDate applicationEndAt, Long totalPrizeAmount, String prizeInfo,
            String activityKind, List<String> category, Boolean isTeam, String summary, List<String> images,
            List<ExtractedUrl> urls
    ) {
    }

    public record ExtractedUrl(String type, String url) {
    }

    public static class ExtractionUnavailable extends RuntimeException {
        private final HttpStatus status;
        private final boolean retryable;

        public ExtractionUnavailable(HttpStatus status, String message) {
            this(status, message, false);
        }

        public ExtractionUnavailable(HttpStatus status, String message, boolean retryable) {
            super(message);
            this.status = status;
            this.retryable = retryable;
        }

        public HttpStatus status() {
            return status;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
