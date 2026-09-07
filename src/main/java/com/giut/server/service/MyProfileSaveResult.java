package com.giut.server.service;

import com.giut.server.dto.response.MyProfileResponse;

public record MyProfileSaveResult(
        MyProfileResponse response,
        boolean created
) {
}
