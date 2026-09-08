package com.giut.server.dto.response;

import java.util.List;

public record PublicProfileListResponse(
        List<PublicProfileResponse> profiles
) {
}
