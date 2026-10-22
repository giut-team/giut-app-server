package com.giut.server.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserBlockResponse(
        Long userId,
        @Schema(description = "현재 로그인 사용자가 이 사용자를 차단했는지 여부. 상대의 차단 여부는 공개하지 않습니다.")
        boolean blocked
) {}
