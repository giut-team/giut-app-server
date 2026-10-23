package com.giut.server.user.dto.response;

import java.time.Instant;
import java.util.List;

public record BlockedUserListResponse(
        List<BlockedUser> users, int page, int size, long totalElements, int totalPages, boolean hasNext
) {
    public record BlockedUser(Long userId, String nickname, Instant blockedAt) {}
}
