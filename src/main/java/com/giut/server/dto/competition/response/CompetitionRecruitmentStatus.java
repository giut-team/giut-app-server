package com.giut.server.dto.competition.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompetitionRecruitmentStatus {
    UPCOMING("모집 예정"),
    OPEN("모집 중"),
    CLOSED("모집 마감");

    private final String displayName;
}
