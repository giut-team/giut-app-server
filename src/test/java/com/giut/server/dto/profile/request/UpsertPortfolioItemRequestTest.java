package com.giut.server.dto.profile.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.dto.profile.common.PortfolioItemDto;
import com.giut.server.entity.PortfolioItem;
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
                  "markdownContent": "## 소개",
                  "skillTagIds": [1]
                }
                """, UpsertPortfolioItemRequest.class);

        assertThat(request.title()).isEqualTo("프로젝트");
        assertThat(request.skillTagIds()).containsExactly(1L);
    }

    @Test
    void responseStillIncludesFalseRepresentative() throws Exception {
        PortfolioItem portfolioItem = PortfolioItem.create(
                null, "https://example.com/image.png", "프로젝트", "설명",
                null, null, null, "## 소개"
        );
        PortfolioItemDto response = PortfolioItemDto.from(portfolioItem, List.of());

        assertThat(response.representative()).isFalse();
        assertThat(objectMapper.writeValueAsString(response)).contains("\"representative\":false");
    }
}
