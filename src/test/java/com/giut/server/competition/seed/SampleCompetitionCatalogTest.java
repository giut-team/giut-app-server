package com.giut.server.competition.seed;

import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SampleCompetitionCatalogTest {

    @Test
    void allTenSamplesPassRequestValidationAndHaveDistinctSourceUrlsAndValidDates() {
        Instant start = Instant.parse("2026-10-26T06:00:00.123456789Z");
        var samples = new SampleCompetitionCatalog().competitions(start, UUID.randomUUID());
        var normalizer = new CompetitionUrlNormalizer();
        Set<String> hashes = new HashSet<>();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(samples).hasSize(10);
            for (var sample : samples) {
                assertThat(validator.validate(sample)).as(sample.title()).isEmpty();
                assertThat(sample.publicationStatus()).isEqualTo(Competition.PublicationStatus.PUBLISHED);
                assertThat(sample.summary()).startsWith("[테스트 데이터]");
                assertThat(sample.applicationStartAt()).isEqualTo(start);
                assertThat(sample.applicationEndAt()).isEqualTo(start.plus(Duration.ofDays(30)));
                assertThat(sample.urls()).hasSize(1);
                var source = sample.urls().getFirst();
                assertThat(source.primary()).isTrue();
                assertThat(source.type()).isEqualTo(CompetitionUrl.Type.RECRUITMENT);
                assertThat(source.url()).startsWith("https://example.com/giut-test/samples/");
                assertThat(hashes.add(normalizer.normalize(source.url()).hash())).isTrue();
            }
        }

        assertThat(samples.getFirst().title()).isEqualTo("국부펀드 KIC, AI 에이전트 공모전");
        assertThat(samples.getFirst().summary()).endsWith("참고 출처: https://daker.ai/public/hackathons/kic-ai-agent-competition");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-31T23:59:59Z", "2028-02-29T23:59:59Z", "2026-12-31T23:59:59Z"})
    void thirtyDaysIsAnElapsedDurationEvenAcrossMonthLeapDayAndYearBoundaries(String value) {
        Instant start = Instant.parse(value);
        var samples = new SampleCompetitionCatalog().competitions(start, UUID.randomUUID());
        assertThat(samples).allSatisfy(sample -> {
            assertThat(sample.applicationStartAt()).isEqualTo(start);
            assertThat(Duration.between(start, sample.applicationEndAt())).isEqualTo(Duration.ofDays(30));
        });
    }

    @Test
    void identicalInvocationTimesStillHaveDisjointNormalizedUrlsAndPreserveContent() {
        var catalog = new SampleCompetitionCatalog();
        Instant start = Instant.parse("2026-10-26T06:00:00Z");
        var first = catalog.competitions(start, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var second = catalog.competitions(start, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var normalizer = new CompetitionUrlNormalizer();
        var firstHashes = first.stream().map(sample -> normalizer.normalize(sample.urls().getFirst().url()).hash()).toList();
        assertThat(second.stream().map(sample -> normalizer.normalize(sample.urls().getFirst().url()).hash()).toList())
                .doesNotContainAnyElementsOf(firstHashes);
        for (int index = 0; index < 10; index++) {
            assertThat(second.get(index)).usingRecursiveComparison().ignoringFields("urls").isEqualTo(first.get(index));
        }
    }

    @Test
    void laterInvocationAdvancesThePeriodWithoutChangingTheEarlierBatch() {
        var catalog = new SampleCompetitionCatalog();
        Instant firstStart = Instant.parse("2026-10-26T06:00:00Z");
        var first = catalog.competitions(firstStart, UUID.randomUUID());
        Instant nextStart = firstStart.plus(Duration.ofDays(7));
        var second = catalog.competitions(nextStart, UUID.randomUUID());
        assertThat(first).allSatisfy(sample -> assertThat(sample.applicationStartAt()).isEqualTo(firstStart));
        assertThat(second).allSatisfy(sample -> {
            assertThat(sample.applicationStartAt()).isEqualTo(nextStart);
            assertThat(sample.applicationEndAt()).isEqualTo(nextStart.plus(Duration.ofDays(30)));
        });
    }
}

