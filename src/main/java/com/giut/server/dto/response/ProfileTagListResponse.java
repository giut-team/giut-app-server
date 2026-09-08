package com.giut.server.dto.response;

import com.giut.server.entity.ProfileTag;

import java.util.List;

public record ProfileTagListResponse(
        ProfileTag.TagType type,
        List<ProfileTagResponse> tags
) {
}
