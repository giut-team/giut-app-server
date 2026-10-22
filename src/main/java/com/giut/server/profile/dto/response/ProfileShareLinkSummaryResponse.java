package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileShareLink;

import java.time.Instant;

/** 보안을 위해 발급 후 목록에는 원본 공유 토큰을 다시 표시하지 않는다. */
public record ProfileShareLinkSummaryResponse(
        Long id,
        Instant createdAt,
        Instant expiresAt
) {
    public static ProfileShareLinkSummaryResponse from(ProfileShareLink link) {
        return new ProfileShareLinkSummaryResponse(link.getId(), link.getCreatedAt(), link.getExpiresAt());
    }
}
