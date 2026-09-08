package com.giut.server.dto.response;

public record CreateSkillTagResponse(
        boolean created,
        ProfileTagResponse skill
) {
}
