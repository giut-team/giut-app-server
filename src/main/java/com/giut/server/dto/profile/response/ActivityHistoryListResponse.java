package com.giut.server.dto.profile.response;

import com.giut.server.dto.profile.common.ActivityHistoryDto;

import java.util.List;

public record ActivityHistoryListResponse(
        List<ActivityHistoryDto> activityHistories
) {
}
