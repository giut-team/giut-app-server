package com.giut.server.competition.dto.response;

/**
 * 공모전의 최초 등록 경로. 회원 제출은 제출 메타데이터로, 관리자와 AI 등록은 최초 검증 로그로 판별한다.
 */
public enum CompetitionRegistrationSource {
    ADMIN,
    USER,
    AI,
    UNKNOWN
}
