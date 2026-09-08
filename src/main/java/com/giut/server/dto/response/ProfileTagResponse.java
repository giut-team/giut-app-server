package com.giut.server.dto.response;

import com.giut.server.entity.ProfileTag;

public record ProfileTagResponse(
        Long id,
        ProfileTag.TagType type,
        String name
) {
    public static ProfileTagResponse from(ProfileTag tag) {
        return new ProfileTagResponse(tag.getId(), tag.getTagType(), tag.getName());
    }
}
