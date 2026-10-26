package com.giut.server.competition.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.competition.entity.Competition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionVerificationResponseTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest(name = "{0} / {1} => verified={2}")
    @MethodSource("statusCombinations")
    void listAndDetailUseActualVerificationStatusAndSerializeABoolean(
            Competition.PublicationStatus publication,
            Competition.VerificationStatus verification,
            boolean expected
    ) {
        Competition competition = Competition.create("공모전", Competition.Category.WEB_MOBILE_IT,
                "주최", "대학생", "소개", Instant.parse("2026-10-26T00:00:00Z"),
                Instant.parse("2026-11-25T00:00:00Z"), publication, verification, null);

        var list = PublicCompetitionResponse.from(competition, CompetitionRecruitmentStatus.OPEN, null, 0, 0);
        var detail = PublicCompetitionDetailResponse.from(competition, CompetitionRecruitmentStatus.OPEN,
                0, false, List.of(), 0, 0, List.of());

        assertThat(competition.isVerified()).isEqualTo(expected);
        assertThat(list.verified()).isEqualTo(expected);
        assertThat(detail.verified()).isEqualTo(expected);
        for (Object response : List.of(list, detail)) {
            var json = mapper.valueToTree(response);
            assertThat(json.path("verified").isBoolean()).isTrue();
            assertThat(json.path("verified").asBoolean()).isEqualTo(expected);
            assertThat(json.has("isVerified")).isFalse();
            assertThat(json.has("verificationStatus")).isFalse();
        }
        // Mapping must not approve, publish or otherwise mutate the entity.
        assertThat(competition.getPublicationStatus()).isEqualTo(publication);
        assertThat(competition.getVerificationStatus()).isEqualTo(verification);
    }

    private static Stream<Arguments> statusCombinations() {
        return Arrays.stream(Competition.PublicationStatus.values()).flatMap(publication ->
                Arrays.stream(Competition.VerificationStatus.values()).map(verification ->
                        Arguments.of(publication, verification,
                                verification == Competition.VerificationStatus.AUTO_VERIFIED
                                        || verification == Competition.VerificationStatus.MANUALLY_VERIFIED)));
    }
}
