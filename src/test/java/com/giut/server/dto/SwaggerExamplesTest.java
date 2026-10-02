package com.giut.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerExamplesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void responseExamplesAreValidJsonWithoutEmptyArrays() throws Exception {
        for (String example : List.of(
                SwaggerExamples.PORTFOLIO_ITEM,
                SwaggerExamples.PORTFOLIO_LIST,
                SwaggerExamples.PORTFOLIO_SHOWCASE,
                SwaggerExamples.ACTIVITY_HISTORY,
                SwaggerExamples.ACTIVITY_HISTORY_LIST,
                SwaggerExamples.COMPETITION_LIST,
                SwaggerExamples.COMPETITION_PAGE,
                SwaggerExamples.COMPETITION_DETAIL,
                SwaggerExamples.ADMIN_COMPETITION,
                SwaggerExamples.ADMIN_COMPETITION_PUBLISHED,
                SwaggerExamples.PROFILE_REPORT_LIST,
                SwaggerExamples.SHARED_PROFILE
        )) {
            assertThat(objectMapper.readTree(example)).isNotNull();
            assertThat(example).doesNotContain("[]");
        }
    }

    @Test
    void listExamplesContainAtLeastOneItem() throws Exception {
        assertNonEmptyArray(SwaggerExamples.PORTFOLIO_LIST, "/portfolioItems");
        assertNonEmptyArray(SwaggerExamples.PORTFOLIO_SHOWCASE, "/portfolioItems");
        assertNonEmptyArray(SwaggerExamples.ACTIVITY_HISTORY_LIST, "/activityHistories");
        assertNonEmptyArray(SwaggerExamples.COMPETITION_LIST, "");
        assertNonEmptyArray(SwaggerExamples.COMPETITION_PAGE, "/competitions");
        assertNonEmptyArray(SwaggerExamples.COMPETITION_DETAIL, "/urls");
        assertNonEmptyArray(SwaggerExamples.COMPETITION_DETAIL, "/teams");
        assertNonEmptyArray(SwaggerExamples.ADMIN_COMPETITION, "/urls");
        assertNonEmptyArray(SwaggerExamples.ADMIN_COMPETITION_PUBLISHED, "/urls");
        assertNonEmptyArray(SwaggerExamples.PROFILE_REPORT_LIST, "/reports");
        assertNonEmptyArray(SwaggerExamples.SHARED_PROFILE, "/primaryRoles");
        assertNonEmptyArray(SwaggerExamples.SHARED_PROFILE, "/skills");
    }

    @Test
    void competitionExamplesShowConsistentTeamCounts() throws Exception {
        JsonNode page = objectMapper.readTree(SwaggerExamples.COMPETITION_PAGE);
        assertThat(page.at("/competitions/0/teamCount").asLong()).isEqualTo(3);

        JsonNode detail = objectMapper.readTree(SwaggerExamples.COMPETITION_DETAIL);
        JsonNode teams = detail.path("teams");
        assertThat(detail.path("teamCount").asLong()).isEqualTo(teams.size());
        assertThat(detail.path("recruitingTeamCount").asLong())
                .isEqualTo(java.util.stream.StreamSupport.stream(teams.spliterator(), false)
                        .filter(team -> "RECRUITING".equals(team.path("status").asText()))
                        .count());
        assertThat(teams.get(0).path("myTeam").asBoolean()).isTrue();
        assertThat(teams.get(1).path("myTeam").asBoolean()).isFalse();
    }

    private void assertNonEmptyArray(String example, String pointer) throws Exception {
        JsonNode items = objectMapper.readTree(example).at(pointer);
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isPositive();
    }
}
