package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileRole;

public record ProfilePrimaryRoleResponse(
        String code,
        String name
) {
    public static ProfilePrimaryRoleResponse from(ProfileRole.PrimaryRole primaryRole) {
        return new ProfilePrimaryRoleResponse(primaryRole.name(), primaryRole.getDisplayName());
    }
}
