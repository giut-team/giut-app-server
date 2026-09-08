package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileTag;

import java.util.List;

public record ProfileTagResponse(
        Long id,
        ProfileTag.TagType type,
        String name,
        List<ProfileRoleResponse> relatedRoles
) {
    public static ProfileTagResponse from(ProfileTag tag, List<ProfileRoleResponse> relatedRoles) {
        return new ProfileTagResponse(tag.getId(), tag.getTagType(), tag.getName(), relatedRoles);
    }
}
