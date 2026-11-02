package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;

/** AI's external codes are independent of the existing public and database codes. */
public enum AiActivityType {
    CONTEST, COMPETITION, HACKATHON, SUPPORTERS, VOLUNTEER, STARTUP,
    EDUCATION, OVERSEAS_EXPERIENCE, NETWORKING, ETC, NOT_ACTIVITY, MULTIPLE_ACTIVITIES;

    public Competition.ActivityType toActivityType() {
        return switch (this) {
            case CONTEST -> Competition.ActivityType.COMPETITION;
            case COMPETITION -> Competition.ActivityType.CONTEST;
            case HACKATHON -> Competition.ActivityType.HACKATHON;
            case SUPPORTERS -> Competition.ActivityType.SUPPORTERS;
            case VOLUNTEER -> Competition.ActivityType.VOLUNTEERING;
            case STARTUP -> Competition.ActivityType.STARTUP;
            case EDUCATION -> Competition.ActivityType.EDUCATION_PROGRAM;
            case OVERSEAS_EXPERIENCE -> Competition.ActivityType.OVERSEAS_EXPLORATION;
            case NETWORKING -> Competition.ActivityType.EVENT_NETWORKING;
            case ETC -> Competition.ActivityType.ETC;
            case NOT_ACTIVITY -> Competition.ActivityType.NOT_ACTIVITY;
            case MULTIPLE_ACTIVITIES -> throw new IllegalArgumentException("복수 활동 유형은 검토가 필요해 자동 등록할 수 없습니다.");
        };
    }
}
