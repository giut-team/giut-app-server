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
    private final WebClient webClient;
    private final String endpoint;

    public AiCompetitionExtractClient(WebClient webClient, @Value("${app.ai-extraction.url:}") String endpoint) {
        this.webClient = webClient;
        this.endpoint = endpoint;
    }

    public boolean isConfigured() {
        return !endpoint.isBlank();
    }

    /**
     * 호출 스레드를 막고 기다린다. 비동기 작업자는 DB 트랜잭션 밖의 전용 스레드에서만 호출한다.
     * 실패는 {@link ExtractionUnavailable}로 돌려주며(시간 초과 504, 응답 오류 502, 미설정 503), 재시도 가능 여부는 {@link ExtractionUnavailable#retryable()}로 구분한다.
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
            return new ExtractionUnavailable(HttpStatus.GATEWAY_TIMEOUT, "공모전 추출 응답 시간이 초과됐습니다.", true,
                    "AI 응답 시간 초과(TimeoutException)");
        }
        // 연결 실패, AI 서버 과부하(5xx·429·408)는 잠시 뒤 같은 요청이 성공할 수 있다.
        // 그 밖의 4xx·응답 형식 오류는 같은 입력으로 재시도해도 같은 결과일 가능성이 높아 재시도하지 않는다.
        boolean retryable = error instanceof WebClientRequestException
                || error instanceof WebClientResponseException response
                && (response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429
                || response.getStatusCode().value() == 408);
        return new ExtractionUnavailable(HttpStatus.BAD_GATEWAY, "공모전 추출 서비스 응답을 받지 못했습니다.", retryable,
                describe(error));
    }

    /** 관리자가 원인을 짐작할 수 있는 오류 종류와 HTTP 상태만 남긴다. 요청·응답 본문, URL, 예외 메시지는 담지 않는다. */
    private static String describe(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return "AI 서버가 HTTP " + response.getStatusCode().value() + "로 응답";
        }
        if (error instanceof WebClientRequestException) {
            Throwable cause = error.getCause();
            return "AI 서버 연결 실패(" + (cause == null ? error : cause).getClass().getSimpleName() + ")";
        }
        return "AI 응답 처리 실패(" + error.getClass().getSimpleName() + ")";
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
        private final String detail;

        public ExtractionUnavailable(HttpStatus status, String message) {
            this(status, message, false);
        }

        public ExtractionUnavailable(HttpStatus status, String message, boolean retryable) {
            this(status, message, retryable, null);
        }

        public ExtractionUnavailable(HttpStatus status, String message, boolean retryable, String detail) {
            super(message);
            this.status = status;
            this.retryable = retryable;
            this.detail = detail;
        }

        /** 관리자용 원인 요약. 회원 응답에는 쓰지 않는다. 없을 수 있다. */
        public String detail() {
            return detail;
        }

        public HttpStatus status() {
            return status;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
