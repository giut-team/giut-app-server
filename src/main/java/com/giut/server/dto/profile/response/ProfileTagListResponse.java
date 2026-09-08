package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileTag;

import java.util.List;

public record ProfileTagListResponse(
        ProfileTag.TagType type,
        List<ProfileTagResponse> tags
) {
}
