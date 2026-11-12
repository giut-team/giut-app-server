package com.giut.server.competition.dto.response;

/**
 * 공모전이 어떤 경로로 처음 등록됐는지. Competition 테이블에는 출처 컬럼이 없으므로
 * 생성 시 함께 남는 검증 로그로 판별하고, 근거가 없거나 서로 상충하면 UNKNOWN으로 둔다.
 */
public enum CompetitionRegistrationSource {
    ADMIN,
    AI,
    UNKNOWN
}
