package com.giut.server.competition.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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

    public Extraction extract(String sourceUrl) {
        if (endpoint.isBlank()) {
            throw new ExtractionUnavailable(HttpStatus.SERVICE_UNAVAILABLE, "공모전 추출 서비스가 설정되지 않았습니다.");
        }
        return webClient.post().uri(endpoint)
                .bodyValue(Map.of("url", sourceUrl))
                .retrieve()
                .bodyToMono(Extraction.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("Empty extraction response")))
                .timeout(Duration.ofSeconds(20))
                .onErrorMap(TimeoutException.class, error ->
                        new ExtractionUnavailable(HttpStatus.GATEWAY_TIMEOUT, "공모전 추출 응답 시간이 초과됐습니다."))
                .onErrorMap(error -> !(error instanceof ExtractionUnavailable), error ->
                        new ExtractionUnavailable(HttpStatus.BAD_GATEWAY, "공모전 추출 서비스 응답을 받지 못했습니다."))
                .block();
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

        public ExtractionUnavailable(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }
}
