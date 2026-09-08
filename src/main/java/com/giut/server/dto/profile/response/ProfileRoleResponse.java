package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileRole;

public record ProfileRoleResponse(
        String code,
        String name
) {
    public static ProfileRoleResponse from(ProfileRole role) {
        return new ProfileRoleResponse(role.getCode(), role.getName());
    }
}
