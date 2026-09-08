package com.giut.server.dto.profile.common;

import com.giut.server.entity.ProfileRole;

/** 대표 역할과 세부 역할이 공통으로 사용하는 코드·이름 응답이다. */
public record ProfileCodeNameResponse(
        String code,
        String name
) {
    public static ProfileCodeNameResponse from(ProfileRole role) {
        return new ProfileCodeNameResponse(role.getCode(), role.getName());
    }

    public static ProfileCodeNameResponse from(ProfileRole.PrimaryRole primaryRole) {
        return new ProfileCodeNameResponse(primaryRole.name(), primaryRole.getDisplayName());
    }
}
