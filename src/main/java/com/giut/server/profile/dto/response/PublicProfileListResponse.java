package com.giut.server.profile.dto.response;

import java.util.List;

public record PublicProfileListResponse(
        List<PublicProfileResponse> profiles,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
