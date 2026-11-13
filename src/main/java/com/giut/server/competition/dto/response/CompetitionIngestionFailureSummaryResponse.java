package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.CompetitionIngestionFailure.FailureType;

import java.time.Instant;
import java.util.Map;

/**
 * 기간 안에 기록된 AI 배치 등록 실패의 유형별 건수. 모든 유형을 0건이어도 포함한다.
 * 실패 기록만 집계하므로 생성에 성공한 건수는 없다(배치별 생성 건수는 서버 로그 AI_INGESTION_BATCH에 있다).
 */
public record CompetitionIngestionFailureSummaryResponse(
        Instant from,
        Instant to,
        long total,
        Map<FailureType, Long> byType
) {
}
