package com.giut.server.dto.profile.response;

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
