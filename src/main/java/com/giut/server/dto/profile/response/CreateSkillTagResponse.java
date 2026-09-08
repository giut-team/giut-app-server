package com.giut.server.dto.profile.response;

public record CreateSkillTagResponse(
        boolean created,
        ProfileTagResponse skill
) {
}
