package com.giut.server.profile.dto.response;

import java.util.List;

public record ProfileShareLinkListResponse(List<ProfileShareLinkSummaryResponse> links) {
}
