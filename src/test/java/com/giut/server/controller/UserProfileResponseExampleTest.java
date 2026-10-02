package com.giut.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

        assertThat(profile.path("portfolioItems").size()).isGreaterThan(0);
        assertThat(profile.path("activityHistories").size()).isGreaterThan(0);
        assertThat(profile.has("department")).isFalse();
        assertThat(profile.has("gender")).isFalse();
        assertThat(profile.has("activityStatus")).isFalse();
        assertThat(root.path("summary").path("scrapCount").asLong())
                .isEqualTo(root.path("summary").path("competitionScrapCount").asLong()
                        + root.path("summary").path("teamScrapCount").asLong());
    }
}
