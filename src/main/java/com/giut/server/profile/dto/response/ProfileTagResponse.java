package com.giut.server.profile.dto.response;

import com.giut.server.profile.entity.ProfileTag;
import com.giut.server.profile.dto.common.ProfileCodeNameResponse;

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
