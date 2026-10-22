package com.giut.server.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.profile.dto.response.PublicProfileDetailResponse;
import com.giut.server.profile.service.UserProfileService;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicProfileControllerTest {
    @Test
    void detailUsesAuthenticatedViewerAndDoesNotCachePersonalizedResponse() {
        var service = mock(UserProfileService.class);
        var controller = new PublicProfileController(service);
        var authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("12");
        var expected = new PublicProfileDetailResponse("상대", true, 2, true, 5, null);
        when(service.getPublicProfile(12L, 13L)).thenReturn(expected);

        var response = controller.getPublicProfile(13L, authentication);

        assertThat(response.getBody()).isSameAs(expected);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).getPublicProfile(12L, 13L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void swaggerDetailExampleContainsRecommendationAndExistingProfileFields() throws Exception {
        var responses = PublicProfileController.class.getMethod("getPublicProfile", Long.class, Authentication.class)
                .getAnnotation(ApiResponses.class);
        var example = Arrays.stream(responses.value()).filter(response -> response.responseCode().equals("200"))
                .findFirst().orElseThrow().content()[0].examples()[0].value();
        var root = new ObjectMapper().readTree(example);

        assertThat(root.path("recommended").isBoolean()).isTrue();
        assertThat(root.path("recommended").booleanValue()).isTrue();
        assertThat(root.path("recommendationCount").longValue()).isEqualTo(5);
        assertThat(root.path("receivedProposalCount").longValue()).isEqualTo(2);
        assertThat(root.path("profile").path("portfolioItems").size()).isPositive();
        assertThat(root.path("profile").path("activityHistories").size()).isPositive();
    }
}
