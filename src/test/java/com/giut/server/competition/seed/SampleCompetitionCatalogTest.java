package com.giut.server.competition.seed;

import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SampleCompetitionCatalogTest {

    @Test
    void allTenSamplesPassRequestValidationAndHaveDistinctSourceUrlsAndValidDates() {
        var samples = new SampleCompetitionCatalog().competitions();
        var normalizer = new CompetitionUrlNormalizer();
        Set<String> hashes = new HashSet<>();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(samples).hasSize(10);
            for (var sample : samples) {
                assertThat(validator.validate(sample)).as(sample.title()).isEmpty();
                assertThat(sample.publicationStatus()).isEqualTo(Competition.PublicationStatus.PUBLISHED);
                assertThat(sample.summary()).startsWith("[테스트 데이터]");
                assertThat(sample.applicationEndAt()).isAfter(sample.applicationStartAt());
                assertThat(sample.urls()).hasSize(1);
                var source = sample.urls().getFirst();
                assertThat(source.primary()).isTrue();
                assertThat(source.type()).isEqualTo(CompetitionUrl.Type.SOURCE_ORIGINAL);
                assertThat(hashes.add(normalizer.normalize(source.url()).hash())).isTrue();
            }
        }

        // The KIC deadline is 10:00 in Korea, which must be persisted as 01:00 UTC.
        assertThat(samples.getFirst().applicationEndAt()).isEqualTo(Instant.parse("2026-11-12T01:00:00Z"));
    }
}

