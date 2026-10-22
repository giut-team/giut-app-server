package com.giut.server.profile.dto.response;

import java.util.List;
import com.giut.server.profile.dto.common.ProfileCodeNameResponse;

public record ProfileRoleListResponse(
        String primaryRole,
        String primaryRoleName,
        List<ProfileCodeNameResponse> roles
) {
}
