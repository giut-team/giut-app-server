package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileTag;

import java.util.List;

public record ProfileTagListResponse(
        ProfileTag.TagType type,
        List<ProfileTagResponse> tags
) {
}
