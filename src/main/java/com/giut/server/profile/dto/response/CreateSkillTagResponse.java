package com.giut.server.profile.dto.response;

public record CreateSkillTagResponse(
        boolean created,
        ProfileTagResponse skill
) {
}
