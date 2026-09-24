package com.giut.server.dto.profile.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.giut.server.entity.PortfolioItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "포트폴리오 항목 생성·수정 요청 및 응답")
public record PortfolioItemDto(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "포트폴리오 항목 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,

        @Schema(description = "활동 사진 URL", example = "https://cdn.giut.com/portfolio/data-contest.png")
        @NotBlank(message = "포트폴리오 사진 URL은 필수입니다.")
        @Pattern(regexp = "https?://.+", message = "포트폴리오 사진 URL은 http 또는 https로 시작해야 합니다.")
        @Size(max = 2048, message = "포트폴리오 사진 URL은 2048자 이하여야 합니다.")
        String imageUrl,

        @Schema(description = "포트폴리오 제목", example = "서울시 데이터 공모전 발표")
        @NotBlank(message = "포트폴리오 제목은 필수입니다.")
        @Size(max = 100, message = "포트폴리오 제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "카드에 표시할 짧은 설명", example = "데이터 정책부터 발표까지 맡았어요.")
        @NotBlank(message = "포트폴리오 캡션은 필수입니다.")
        @Size(max = 200, message = "포트폴리오 캡션은 200자 이하여야 합니다.")
        String caption,

        @Schema(description = "상세 화면에 표시할 활동 내용", example = "문제 정의, 데이터 분석, 발표 자료 제작을 담당했습니다.")
        @NotBlank(message = "포트폴리오 내용은 필수입니다.")
        @Size(max = 2000, message = "포트폴리오 내용은 2000자 이하여야 합니다.")
        String content,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "프로필 내 노출 순서", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Integer displayOrder
) {
    public static PortfolioItemDto from(PortfolioItem portfolioItem) {
        return new PortfolioItemDto(
                portfolioItem.getId(),
                portfolioItem.getImageUrl(),
                portfolioItem.getTitle(),
                portfolioItem.getCaption(),
                portfolioItem.getContent(),
                portfolioItem.getDisplayOrder()
        );
    }
}
