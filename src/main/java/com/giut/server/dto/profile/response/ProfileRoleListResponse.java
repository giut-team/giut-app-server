package com.giut.server.dto.profile.response;

import java.util.List;
import com.giut.server.dto.profile.common.ProfileCodeNameResponse;

public record ProfileRoleListResponse(
        String primaryRole,
        String primaryRoleName,
        List<ProfileCodeNameResponse> roles
) {
}
