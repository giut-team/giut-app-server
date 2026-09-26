package com.giut.server.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "포트폴리오 항목 노출 순서 변경 요청")
public record PortfolioItemOrderRequest(
        @Schema(description = "변경할 내 포트폴리오 ID 전체 목록. 첫 번째 항목부터 내 목록에 표시됩니다.", example = "[3, 1, 2]")
        @NotEmpty(message = "포트폴리오 순서 목록은 비어 있을 수 없습니다.")
        List<@NotNull(message = "포트폴리오 ID는 null일 수 없습니다.") Long> portfolioItemIds
) {
}
