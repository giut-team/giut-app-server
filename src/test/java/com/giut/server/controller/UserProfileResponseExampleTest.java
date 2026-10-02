package com.giut.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.dto.profile.request.PutMyProfileRequest;
import com.giut.server.dto.profile.request.UpdateMyProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileResponseExampleTest {

    @Test
    void myProfileExampleContainsPortfolioActivityAndCountsWithoutRemovedCodes() throws Exception {
        ApiResponses responses = UserProfileController.class
                .getMethod("getMyProfile", Authentication.class)
                .getAnnotation(ApiResponses.class);
        ApiResponse success = Arrays.stream(responses.value())
                .filter(response -> response.responseCode().equals("200"))
                .findFirst()
                .orElseThrow();
        String example = success.content()[0].examples()[0].value();
        JsonNode root = new ObjectMapper().readTree(example);
        JsonNode profile = root.path("profile");

        assertThat(profile.path("departmentName").asText()).isEqualTo("컴퓨터과학부");
        assertThat(profile.path("portfolioItems").size()).isGreaterThan(0);
        assertThat(profile.path("portfolioItems").get(0).path("roles").get(0).path("code").asText())
                .isEqualTo("BACKEND_DEVELOPER");
        assertThat(profile.path("activityHistories").size()).isGreaterThan(0);
        assertThat(profile.has("department")).isFalse();
        assertThat(profile.has("gender")).isFalse();
        assertThat(profile.has("activityStatus")).isFalse();
        assertThat(root.path("summary").path("scrapCount").asLong())
                .isEqualTo(root.path("summary").path("competitionScrapCount").asLong()
                        + root.path("summary").path("teamScrapCount").asLong());
    }

    @Test
    void updateExampleMatchesTheUpdateRequestShape() throws Exception {
        Operation operation = UserProfileController.class
                .getMethod("updateMyProfile", Authentication.class, UpdateMyProfileRequest.class)
                .getAnnotation(Operation.class);
        String example = operation.requestBody().content()[0].examples()[0].value();
        JsonNode body = new ObjectMapper().readTree(example);

        assertThat(body.path("departmentname").asText()).isEqualTo("컴퓨터과학부");
        assertThat(body.has("department")).isFalse();
        assertThat(body.path("experienceTagIds").isArray()).isTrue();
        assertThat(body.path("activityHistories").size()).isEqualTo(1);
        assertThat(body.has("nickname")).isFalse();
        assertThat(body.has("gender")).isFalse();
    }

    @Test
    void createExampleUsesExistingNicknameAndDoesNotRequireGender() throws Exception {
        Operation operation = UserProfileController.class
                .getMethod("createMyProfile", Authentication.class, PutMyProfileRequest.class)
                .getAnnotation(Operation.class);
        String example = operation.requestBody().content()[0].examples()[0].value();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode body = objectMapper.readTree(example);
        PutMyProfileRequest request = objectMapper.readValue(example, PutMyProfileRequest.class);

        assertThat(body.path("department").asText()).isEqualTo("컴퓨터과학부");
        assertThat(body.has("departmentname")).isFalse();
        assertThat(body.has("nickname")).isFalse();
        assertThat(body.has("gender")).isFalse();
        assertThat(request.activityHistories()).hasSize(1);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
