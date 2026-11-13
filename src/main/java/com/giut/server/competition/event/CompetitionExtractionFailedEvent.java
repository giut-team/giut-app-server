package com.giut.server.competition.event;

import java.time.Instant;

/**
 * 추출 작업이 더 이상 재시도하지 않고 최종 실패(FAILED)로 확정됐을 때, 그 트랜잭션 안에서 발행한다.
 * 알림과 로그는 commit된 뒤에만 보내므로 롤백된 실패를 알리지 않는다.
 *
 * @param waitingUsers 이 작업 결과를 기다리던(접수했거나 같은 URL로 합류한) 회원 수
 */
public record CompetitionExtractionFailedEvent(
        Long jobId,
        String sourceUrl,
        String errorCode,
        String errorMessage,
        String errorDetail,
        int attemptCount,
        long waitingUsers,
        Instant failedAt
) {
}
