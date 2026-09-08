package com.giut.server.dto.response;

import com.giut.server.dto.response.MyProfileResponse;

public record MyProfileSaveResponse(
        MyProfileResponse response,
        boolean created
) {
}
