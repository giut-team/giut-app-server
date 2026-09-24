package com.giut.server.dto.competition.request;

import com.giut.server.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record UpsertCompetitionRequest(
        @Schema(description = "공모전 제목", example = "서울시 데이터 분석 공모전")
        @NotBlank(message = "공모전 제목은 필수입니다.")
        @Size(max = 200, message = "공모전 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "공모전 카테고리", example = "WEB_MOBILE_IT")
        @NotNull(message = "공모전 카테고리는 필수입니다.")
        Competition.Category category,

        @Schema(description = "주최 기관", example = "서울특별시")
        @Size(max = 150, message = "주최 기관은 150자 이하여야 합니다.")
        String hostOrganization,

        @Schema(description = "참가 대상", example = "전국 대학생 및 대학원생")
        @Size(max = 3000, message = "참가 대상은 3000자 이하여야 합니다.")
        String targetParticipants,

        @Schema(description = "공모전 소개", example = "공공 데이터를 활용한 분석 및 서비스 아이디어 공모전입니다.")
        @NotBlank(message = "공모전 소개는 필수입니다.")
        @Size(max = 10000, message = "공모전 소개는 10000자 이하여야 합니다.")
        String summary,

        @Schema(description = "모집 시작 일시", example = "2026-09-15T00:00:00Z")
        Instant applicationStartAt,

        @Schema(description = "모집 마감 일시", example = "2026-10-15T14:59:59Z")
        Instant applicationEndAt,

        @Schema(description = "게시 상태", example = "PUBLISHED")
        @NotNull(message = "게시 상태는 필수입니다.")
        Competition.PublicationStatus publicationStatus,

        @Schema(description = "공모전 URL 목록")
        @NotEmpty(message = "공모전 URL은 하나 이상 등록해야 합니다.")
        List<@Valid CompetitionUrlRequest> urls
) {
}
