package com.giut.server.profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기본 역할 등록 결과")
public record SeedDefaultProfileRolesResponse(
        @Schema(description = "등록 후 DB의 전체 세부 역할 수. 기존에 추가된 역할도 포함합니다.", example = "15")
        long totalRoleCount
) {
}
