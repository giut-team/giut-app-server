package com.giut.server.dto.profile.response;

import java.time.Instant;

/** sharePath 앞에 프론트엔드 origin을 붙여 공유 URL로 사용한다. */
public record ProfileShareLinkResponse(
        Long id,
        String sharePath,
        Instant createdAt,
        Instant expiresAt
) {
}
