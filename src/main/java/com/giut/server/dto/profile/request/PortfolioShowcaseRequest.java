package com.giut.server.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortfolioShowcaseRequest(
        @Schema(description = "공개 프로필에 노출할 포트폴리오 ID 목록. 전달한 순서대로 노출됩니다.", example = "[15, 11, 9]")
        @NotNull(message = "공개 포트폴리오 목록은 필수입니다.")
        @Size(max = 6, message = "공개 프로필에는 포트폴리오를 최대 6개까지 노출할 수 있습니다.")
        List<Long> portfolioItemIds
) {
}
