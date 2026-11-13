package com.giut.server.competition.service;

import com.giut.server.competition.util.PublicCompetitionSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCompetitionExtractClientTest {
    private static final String ENDPOINT = "https://ai.example.com/api/competitions/extract";

    @Test
    void decodesExtractEndpointDateAndNullableFields() {
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.url().toString()).isEqualTo(ENDPOINT);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("""
                            {"title":"공모전","applicationEndAt":"2099-01-02","category":["IDEA"],
                             "urls":[{"type":"OFFICIAL","url":null}],"images":null}
                            """)
                    .build());
        }).build();

        AiCompetitionExtractClient.Extraction result =
                new AiCompetitionExtractClient(client, ENDPOINT).extract("https://example.com/contest", Duration.ofSeconds(5));

        assertThat(result.applicationEndAt()).isEqualTo(LocalDate.of(2099, 1, 2));
        assertThat(result.applicationStartAt()).isNull();
        assertThat(result.category()).containsExactly("IDEA");
        assertThat(result.urls()).singleElement().satisfies(link -> assertThat(link.url()).isNull());
    }

    @Test
    void mapsInvalidUpstreamResponseToBadGateway() {
        WebClient client = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY).build())).build();

        assertThatThrownBy(() -> new AiCompetitionExtractClient(client, ENDPOINT).extract("https://example.com/contest", Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(AiCompetitionExtractClient.ExtractionUnavailable.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void classifiesOnlyTransientUpstreamFailuresAsRetryable() {
        assertThat(failure(HttpStatus.SERVICE_UNAVAILABLE).retryable()).isTrue();
        assertThat(failure(HttpStatus.TOO_MANY_REQUESTS).retryable()).isTrue();
        assertThat(failure(HttpStatus.UNPROCESSABLE_ENTITY).retryable()).isFalse();
        assertThat(failure(HttpStatus.BAD_REQUEST).retryable()).isFalse();
        assertThat(failure(HttpStatus.SERVICE_UNAVAILABLE).status()).isEqualTo(HttpStatus.BAD_GATEWAY);

        WebClient malformed = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json").body("{not json").build())).build();
        assertThatThrownBy(() -> new AiCompetitionExtractClient(malformed, ENDPOINT).extract("https://example.com/contest", Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(AiCompetitionExtractClient.ExtractionUnavailable.class,
                        error -> assertThat(error.retryable()).isFalse());
    }

    @Test
    void appliesCallerTimeoutAsRetryableGatewayTimeout() {
        WebClient slow = WebClient.builder().exchangeFunction(request -> Mono.never()).build();
        assertThatThrownBy(() -> new AiCompetitionExtractClient(slow, ENDPOINT)
                .extract("https://example.com/contest", Duration.ofMillis(50)))
                .isInstanceOfSatisfying(AiCompetitionExtractClient.ExtractionUnavailable.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(error.retryable()).isTrue();
                });
    }

    @Test
    void unconfiguredEndpointIsNotRetryable() {
        AiCompetitionExtractClient client = new AiCompetitionExtractClient(WebClient.builder().build(), "");
        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.extract("https://example.com/contest", Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(AiCompetitionExtractClient.ExtractionUnavailable.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.retryable()).isFalse();
                });
    }

    private AiCompetitionExtractClient.ExtractionUnavailable failure(HttpStatus upstream) {
        WebClient client = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(upstream).build())).build();
        try {
            new AiCompetitionExtractClient(client, ENDPOINT).extract("https://example.com/contest", Duration.ofSeconds(5));
        } catch (AiCompetitionExtractClient.ExtractionUnavailable error) {
            return error;
        }
        throw new AssertionError("expected failure");
    }

    @Test
    void rejectsLoopbackSourceBeforeAnyExtraction() {
        assertThatThrownBy(() -> new PublicCompetitionSource().requirePublicAddress("http://127.0.0.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공개 인터넷");
    }
}
