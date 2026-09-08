package com.giut.server.dto.profile.response;

public record MyProfileSaveResponse(
        MyProfileResponse response,
        boolean created
) {
}
