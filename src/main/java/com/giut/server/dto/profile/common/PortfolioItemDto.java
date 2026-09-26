package com.giut.server.dto.profile.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.giut.server.entity.PortfolioItem;
import com.giut.server.dto.profile.response.ProfileTagSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

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

        @Schema(description = "프로젝트 시작일", example = "2025-09-01")
        LocalDate projectStartDate,

        @Schema(description = "프로젝트 종료일", example = "2025-12-31")
        LocalDate projectEndDate,

        @Schema(description = "프로젝트 팀 인원", example = "5")
        @Min(value = 1, message = "팀 인원은 1명 이상이어야 합니다.")
        Integer teamSize,

        @Schema(description = "상세 화면에서 렌더링할 Markdown 원문", example = "## 프로젝트 소개\\n\\n분리배출 캠페인의 일정과 산출물을 관리했습니다.\\n\\n## 담당한 일\\n\\n- 회의록 작성\\n- 데이터 집계")
        @NotBlank(message = "포트폴리오 마크다운 내용은 필수입니다.")
        @Size(max = 10000, message = "포트폴리오 마크다운 내용은 10000자 이하여야 합니다.")
        String markdownContent,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(description = "프로젝트에 사용한 기술 스택 태그 ID 목록", example = "[1, 4, 8]", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 5, message = "프로젝트 기술 스택은 최대 5개까지 선택할 수 있습니다.")
        List<Long> skillTagIds,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "프로젝트 기술 스택", accessMode = Schema.AccessMode.READ_ONLY)
        List<ProfileTagSummaryResponse> skillTags,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "내 포트폴리오 전체 목록에서의 정렬 순서", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Integer displayOrder,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "공개 프로필 내 노출 순서. null이면 숨김", example = "1", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
        Integer showcaseOrder,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(description = "대표 포트폴리오 여부", example = "true", accessMode = Schema.AccessMode.READ_ONLY)
        boolean representative
) {
    public static PortfolioItemDto from(
            PortfolioItem portfolioItem,
            List<ProfileTagSummaryResponse> skillTags
    ) {
        return new PortfolioItemDto(
                portfolioItem.getId(),
                portfolioItem.getImageUrl(),
                portfolioItem.getTitle(),
                portfolioItem.getCaption(),
                portfolioItem.getProjectStartDate(),
                portfolioItem.getProjectEndDate(),
                portfolioItem.getTeamSize(),
                portfolioItem.getMarkdownContent(),
                null,
                skillTags,
                portfolioItem.getDisplayOrder(),
                portfolioItem.getShowcaseOrder(),
                portfolioItem.isRepresentative()
        );
    }
}
