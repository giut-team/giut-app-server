package com.giut.server.competition.service;

import com.giut.server.competition.util.PublicCompetitionSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
                new AiCompetitionExtractClient(client, ENDPOINT).extract("https://example.com/contest");

        assertThat(result.applicationEndAt()).isEqualTo(LocalDate.of(2099, 1, 2));
        assertThat(result.applicationStartAt()).isNull();
        assertThat(result.category()).containsExactly("IDEA");
        assertThat(result.urls()).singleElement().satisfies(link -> assertThat(link.url()).isNull());
    }

    @Test
    void mapsInvalidUpstreamResponseToBadGateway() {
        WebClient client = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY).build())).build();

        assertThatThrownBy(() -> new AiCompetitionExtractClient(client, ENDPOINT).extract("https://example.com/contest"))
                .isInstanceOfSatisfying(AiCompetitionExtractClient.ExtractionUnavailable.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void rejectsLoopbackSourceBeforeAnyExtraction() {
        assertThatThrownBy(() -> new PublicCompetitionSource().requirePublicAddress("http://127.0.0.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공개 인터넷");
    }
}
