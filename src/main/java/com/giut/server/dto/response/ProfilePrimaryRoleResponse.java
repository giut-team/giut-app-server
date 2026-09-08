package com.giut.server.dto.response;

import com.giut.server.entity.ProfileRoleCategory;

public record ProfilePrimaryRoleResponse(
        String code,
        String name
) {
    public static ProfilePrimaryRoleResponse from(ProfileRoleCategory primaryRole) {
        return new ProfilePrimaryRoleResponse(primaryRole.getCode(), primaryRole.getName());
    }
}
