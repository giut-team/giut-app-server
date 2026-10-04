package com.giut.server.profile.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "포트폴리오 항목 생성·수정 요청. 새 항목은 공개 목록 1번에 추가되며, 대표 여부는 별도 API에서 설정합니다.")
public record UpsertPortfolioItemRequest(
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

        @Schema(description = "프로젝트에서 맡은 세부 역할 코드 목록", example = "[\"BACKEND_DEVELOPER\", \"DATA_ANALYST\"]")
        @NotEmpty(message = "포트폴리오 역할은 1개 이상 선택해야 합니다.")
        @Size(max = 3, message = "포트폴리오 역할은 최대 3개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "역할 코드는 비어 있을 수 없습니다.") String> roles,

        @Schema(description = "상세 화면에서 렌더링할 Markdown 원문", example = "## 프로젝트 소개\\n\\n분리배출 캠페인의 일정과 산출물을 관리했습니다.")
        @NotBlank(message = "포트폴리오 마크다운 내용은 필수입니다.")
        @Size(max = 10000, message = "포트폴리오 마크다운 내용은 10000자 이하여야 합니다.")
        String markdownContent,

        @Schema(description = "프로젝트에 사용한 기술 스택 태그 ID 목록", example = "[1, 4, 8]")
        @Size(max = 5, message = "프로젝트 기술 스택은 최대 5개까지 선택할 수 있습니다.")
        List<Long> skillTagIds
) {
}
