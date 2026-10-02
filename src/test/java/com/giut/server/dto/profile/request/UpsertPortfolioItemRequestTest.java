package com.giut.server.dto.profile.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;
import com.giut.server.entity.PortfolioItem;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpsertPortfolioItemRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    @Test
    void createRequestWithoutRepresentativeDeserializes() throws Exception {
        UpsertPortfolioItemRequest request = objectMapper.readValue("""
                {
                  "imageUrl": "https://example.com/image.png",
                  "title": "프로젝트",
                  "caption": "설명",
                  "roles": ["BACKEND_DEVELOPER", "DATA_ANALYST"],
                  "markdownContent": "## 소개",
                  "skillTagIds": [1]
                }
                """, UpsertPortfolioItemRequest.class);

        assertThat(request.title()).isEqualTo("프로젝트");
        assertThat(request.roles()).containsExactly("BACKEND_DEVELOPER", "DATA_ANALYST");
        assertThat(request.skillTagIds()).containsExactly(1L);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rolesAreRequired() throws Exception {
        String json = """
                {
                  "imageUrl": "https://example.com/image.png",
                  "title": "프로젝트",
                  "caption": "설명",
                  "markdownContent": "## 소개"
                }
                """;
        UpsertPortfolioItemRequest missingRoles = objectMapper.readValue(json, UpsertPortfolioItemRequest.class);
        UpsertPortfolioItemRequest emptyRoles = objectMapper.readValue(
                json.replace("\"markdownContent\"", "\"roles\": [], \"markdownContent\""),
                UpsertPortfolioItemRequest.class
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(missingRoles))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("roles"));
            assertThat(factory.getValidator().validate(emptyRoles))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("roles"));
        }
    }

    @Test
    void responseStillIncludesFalseRepresentative() throws Exception {
        PortfolioItem portfolioItem = PortfolioItem.create(
                null, "https://example.com/image.png", "프로젝트", "설명",
                null, null, null, "## 소개"
        );
        PortfolioItemDto response = PortfolioItemDto.from(
                portfolioItem, List.of(), List.of(new ProfileCodeNameResponse("BACKEND_DEVELOPER", "백엔드 개발자"))
        );

        assertThat(response.representative()).isFalse();
        assertThat(response.roles()).extracting(ProfileCodeNameResponse::code).containsExactly("BACKEND_DEVELOPER");
        assertThat(objectMapper.writeValueAsString(response)).contains("\"representative\":false");
    }
}
