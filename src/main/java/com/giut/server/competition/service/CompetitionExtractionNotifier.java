package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.CompetitionExtractionResult;
import com.giut.server.competition.repository.CompetitionExtractionJobSubscriptionRepository;
import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.service.NotificationCommand;
import com.giut.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추출 작업이 끝났을 때(성공 또는 최종 실패) 작업을 기다린 모든 회원에게 알림과 푸시 발송 대기를 만든다.
 *
 * <p>{@link CompetitionExtractionJobStore}의 상태 확정 트랜잭션 안에서만 호출된다({@code MANDATORY}). 작업 행을 잠근 채
 * 실행하므로 같은 작업의 알림이 두 번 만들어지지 않고, 상태 확정이 롤백되면 알림도 함께 롤백된다.
 *
 * <p>이동 경로: 공개 중인 공모전이 있으면 {@code /competitions/{id}}(오래 유효), 그 밖에는 추출 결과 화면
 * {@code /competitions/extractions/{jobId}}다. 추출 작업은 보관 기간(성공 24시간·실패 30일) 뒤 삭제되므로,
 * 앱은 작업 조회가 404면 알림 본문과 payload만으로 안내해야 한다.
 */
@Component
@RequiredArgsConstructor
public class CompetitionExtractionNotifier {
    private static final int MAX_TITLE_IN_BODY = 40;

    private final CompetitionExtractionJobSubscriptionRepository subscriptions;
    private final NotificationService notifications;

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifySucceeded(Long jobId, String sourceUrl, CompetitionExtractionResult result, Instant now) {
        String title = result.draft() == null ? null : shorten(result.draft().title());
        Notification.Type type;
        String heading;
        String body;
        switch (result.status()) {
            case SUBMITTED -> {
                type = Notification.Type.COMPETITION_EXTRACTION_SUBMITTED;
                heading = "공모전 등록 요청을 접수했어요";
                body = quoted(title) + "검토 대기로 등록했어요. 관리자 확인 후 공개돼요.";
            }
            case EXISTS -> {
                type = Notification.Type.COMPETITION_EXTRACTION_EXISTS;
                heading = "이미 등록된 공모전이에요";
                body = "요청하신 링크의 공모전이 이미 기웃에 있어요.";
            }
            case INVALID_ACTIVITY -> {
                type = Notification.Type.COMPETITION_EXTRACTION_INVALID;
                heading = "등록할 수 없는 공고예요";
                body = "요청하신 링크는 공모전·대외활동 공고로 확인되지 않았어요.";
            }
            // NEEDS_REVIEW, 그리고 자동 제출 전 단계인 READY가 남는 예외 상황도 회원 확인이 필요하다.
            default -> {
                type = Notification.Type.COMPETITION_EXTRACTION_NEEDS_REVIEW;
                heading = "공모전 정보를 확인해 주세요";
                body = quoted(title) + "추출이 끝났어요. 빠진 정보를 채워 제출해 주세요.";
            }
        }
        Long competitionId = result.competitionId();
        String actionPath = result.status() == CompetitionExtractionResult.Status.EXISTS && competitionId != null
                ? "/competitions/" + competitionId
                : extractionPath(jobId);
        Map<String, Object> payload = basePayload(jobId, sourceUrl);
        payload.put("resultStatus", result.status().name());
        if (competitionId != null) {
            payload.put("competitionId", competitionId);
        }
        create(jobId, type, heading, body, actionPath, payload, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyFailed(Long jobId, String sourceUrl, String errorCode, Instant now) {
        Map<String, Object> payload = basePayload(jobId, sourceUrl);
        if (errorCode != null) {
            payload.put("errorCode", errorCode);
        }
        create(jobId, Notification.Type.COMPETITION_EXTRACTION_FAILED, "공모전 정보를 가져오지 못했어요",
                "링크의 정보를 추출하지 못했어요. 잠시 후 다시 시도하거나 직접 입력해 주세요.", extractionPath(jobId), payload, now);
    }

    private void create(Long jobId, Notification.Type type, String title, String body, String actionPath,
                        Map<String, Object> payload, Instant now) {
        List<NotificationCommand> commands = subscriptions.findUserIdsByJobId(jobId).stream()
                // 작업 하나는 한 번만 끝나므로 (작업, 회원)마다 알림 하나다.
                .map(userId -> new NotificationCommand(userId, type, title, body, actionPath, payload,
                        "competition-extraction:" + jobId + ":" + userId))
                .toList();
        notifications.createAll(commands, now);
    }

    private static Map<String, Object> basePayload(Long jobId, String sourceUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        // 푸시 data 전체가 4KB를 넘지 않게 긴 URL은 싣지 않는다(알림함에서는 jobId로 다시 조회한다).
        if (sourceUrl != null && sourceUrl.length() <= 500) {
            payload.put("sourceUrl", sourceUrl);
        }
        return payload;
    }

    private static String extractionPath(Long jobId) {
        return "/competitions/extractions/" + jobId;
    }

    private static String quoted(String title) {
        return title == null ? "" : "‘" + title + "’ ";
    }

    private static String shorten(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.strip();
        return trimmed.length() <= MAX_TITLE_IN_BODY ? trimmed : trimmed.substring(0, MAX_TITLE_IN_BODY) + "…";
    }
}
