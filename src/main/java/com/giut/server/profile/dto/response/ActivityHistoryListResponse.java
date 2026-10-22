package com.giut.server.profile.dto.response;

import com.giut.server.profile.dto.common.ActivityHistoryDto;

import java.util.List;

public record ActivityHistoryListResponse(
        List<ActivityHistoryDto> activityHistories
) {
}
