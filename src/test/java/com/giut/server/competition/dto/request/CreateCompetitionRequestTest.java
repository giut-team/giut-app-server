package com.giut.server.competition.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCompetitionRequestTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void requestContractHasNoVerificationStatusFieldAndKeepsOtherFields() {
        var input = new UpsertCompetitionRequest("공모전", Competition.Category.WEB_MOBILE_IT, "주최", "대학생",
                "소개", null, null, Competition.PublicationStatus.DRAFT,
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.RECRUITMENT, "https://example.com/create", true)));
        var request = CreateCompetitionRequest.from(input);
        assertThat(request.toUpsertRequest()).isEqualTo(input);
        assertThat(Arrays.stream(CreateCompetitionRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("verificationStatus");
        var json = mapper.valueToTree(request);
        assertThat(json.has("verificationStatus")).isFalse();
        assertThat(json.path("publicationStatus").asText()).isEqualTo("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"AUTO_VERIFIED\"", "\"MANUALLY_VERIFIED\"", "\"UNVERIFIED\"",
            "\"MANUAL_REVIEW_PENDING\"", "\"REJECTED\"", "null", "\"NOT_A_STATUS\"", "{\"status\":\"UNVERIFIED\"}"})
    void olderClientsRetiredInputIsIgnoredWithoutBindingAHiddenStatus(String value) throws Exception {
        String json = """
                {"title":"공모전","category":"WEB_MOBILE_IT","summary":"소개","publicationStatus":"DRAFT",
                 "urls":[{"type":"RECRUITMENT","url":"https://example.com/create","primary":true}],
                 "verificationStatus":%s}
                """.formatted(value);
        var request = mapper.readValue(json, CreateCompetitionRequest.class);
        assertThat(request.title()).isEqualTo("공모전");
        assertThat(request.publicationStatus()).isEqualTo(Competition.PublicationStatus.DRAFT);
        assertThat(mapper.valueToTree(request).has("verificationStatus")).isFalse();
    }
}
