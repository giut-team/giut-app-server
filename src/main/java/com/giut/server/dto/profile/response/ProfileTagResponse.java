package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileTag;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;

import java.util.List;

public record ProfileTagResponse(
        Long id,
        ProfileTag.TagType type,
        String name,
        List<ProfileCodeNameResponse> relatedRoles
) {
    public static ProfileTagResponse from(ProfileTag tag, List<ProfileCodeNameResponse> relatedRoles) {
        return new ProfileTagResponse(tag.getId(), tag.getTagType(), tag.getName(), relatedRoles);
    }
}
