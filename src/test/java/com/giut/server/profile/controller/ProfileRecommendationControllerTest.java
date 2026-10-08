package com.giut.server.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.profile.dto.response.ProfileRecommendationResponse;
import com.giut.server.profile.service.ProfileRecommendationService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfileRecommendationControllerTest {
    private final ProfileRecommendationService service = mock(ProfileRecommendationService.class);
    private final ProfileRecommendationController controller = new ProfileRecommendationController(service);
    private final Authentication authentication = mock(Authentication.class);

    @Test
    void recommendUsesAuthenticatedActorNotRequestBody() {
        when(authentication.getName()).thenReturn("1");
        var result = new ProfileRecommendationResponse(12L, true, 7L);
        when(service.recommend(1L, 12L)).thenReturn(result);

        assertThat(controller.recommend(authentication, 12L)).isSameAs(result);
        verify(service).recommend(1L, 12L);
    }

    @Test
    void cancelUsesAuthenticatedActor() {
        when(authentication.getName()).thenReturn("1");
        var result = new ProfileRecommendationResponse(12L, false, 6L);
        when(service.cancelRecommendation(1L, 12L)).thenReturn(result);

        assertThat(controller.cancelRecommendation(authentication, 12L)).isSameAs(result);
        verify(service).cancelRecommendation(1L, 12L);
    }

    @Test
    void queryUsesAuthenticatedActor() {
        when(authentication.getName()).thenReturn("1");
        var result = new ProfileRecommendationResponse(12L, true, 7L);
        when(service.getRecommendation(1L, 12L)).thenReturn(result);

        assertThat(controller.getRecommendation(authentication, 12L)).isSameAs(result);
    }

    @Test
    void swaggerExamplesAreValidAndMatchResponseFields() throws Exception {
        var mapper = new ObjectMapper();
        var post = ProfileRecommendationController.class
                .getMethod("recommend", Authentication.class, Long.class)
                .getAnnotation(ApiResponses.class).value()[0];
        var delete = ProfileRecommendationController.class
                .getMethod("cancelRecommendation", Authentication.class, Long.class)
                .getAnnotation(ApiResponse.class);
        var get = ProfileRecommendationController.class
                .getMethod("getRecommendation", Authentication.class, Long.class)
                .getAnnotation(ApiResponse.class);
        for (var response : new ApiResponse[]{post, delete, get}) {
            var example = mapper.readValue(response.content()[0].examples()[0].value(),
                    ProfileRecommendationResponse.class);
            assertThat(example.userId()).isEqualTo(12L);
            assertThat(example.recommendationCount()).isPositive();
        }
    }
}
