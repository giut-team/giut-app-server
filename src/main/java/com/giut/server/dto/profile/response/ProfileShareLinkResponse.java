package com.giut.server.dto.profile.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 프론트엔드가 token으로 공유 URL을 구성한다. */
public record ProfileShareLinkResponse(
        Long id,
        @Schema(description = "공유 URL을 구성할 때 사용하는 토큰", example = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcdefg")
        String token,
        Instant createdAt,
        Instant expiresAt
) {
}
