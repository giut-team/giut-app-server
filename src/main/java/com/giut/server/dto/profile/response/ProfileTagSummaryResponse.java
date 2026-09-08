package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileTag;

public record ProfileTagSummaryResponse(
        Long id,
        ProfileTag.TagType type,
        String name
) {
    public static ProfileTagSummaryResponse from(ProfileTag tag) {
        return new ProfileTagSummaryResponse(tag.getId(), tag.getTagType(), tag.getName());
    }
}
