package com.giut.server.dto.profile.response;

import java.util.List;

public record ProfileRoleListResponse(
        String primaryRole,
        String primaryRoleName,
        List<ProfileRoleResponse> roles
) {
}
