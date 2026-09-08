package com.giut.server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "포트폴리오 항목 노출 순서 변경 요청")
public record PortfolioItemOrderRequest(
        @Schema(description = "변경할 포트폴리오 ID 전체 목록. 첫 번째 항목부터 노출됩니다.", example = "[3, 1, 2]")
        @NotEmpty(message = "포트폴리오 순서 목록은 비어 있을 수 없습니다.")
        @Size(max = 6, message = "포트폴리오는 최대 6개까지 정렬할 수 있습니다.")
        List<@NotNull(message = "포트폴리오 ID는 null일 수 없습니다.") Long> portfolioItemIds
) {
}
