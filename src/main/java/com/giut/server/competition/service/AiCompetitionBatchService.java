package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.entity.CompetitionIngestionFailure;
import com.giut.server.competition.entity.CompetitionIngestionFailure.FailureType;
import com.giut.server.competition.repository.CompetitionIngestionFailureRepository;
import com.giut.server.global.exception.ConflictException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 배치 등록을 공고 단위로 처리한다. 이 클래스에는 트랜잭션을 걸지 않고, 공고마다 다른 Bean인
 * {@link CompetitionService#createByAi}를 호출해 별도 트랜잭션으로 커밋한다. 그래서 한 건이 실패해도
 * 이미 저장된 건이나 뒤의 건에는 영향이 없고, 실패한 건만 실패 테이블과 경고 로그에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AiCompetitionBatchService {
    /** 서버 로그에서 실패 건만 검색할 때 쓰는 고정 접두어. 예: docker logs giut-dev | grep AI_INGESTION_FAILURE */
    public static final String FAILURE_LOG_PREFIX = "AI_INGESTION_FAILURE";
    public static final String BATCH_LOG_PREFIX = "AI_INGESTION_BATCH";
    private static final Logger logger = LoggerFactory.getLogger(AiCompetitionBatchService.class);
    private static final int MAX_LOG_VALUE_LENGTH = 300;

    private final CompetitionService competitionService;
    private final CompetitionIngestionFailureRepository failureRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public List<AdminCompetitionResponse> ingest(List<JsonNode> items) {
        String batchId = UUID.randomUUID().toString();
        Instant receivedAt = Instant.now();
        List<AdminCompetitionResponse> created = new ArrayList<>();
        int failed = 0;
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            try {
                created.add(competitionService.createByAi(toRequest(item)));
            } catch (ItemFailure failure) {
                failed++;
                recordFailure(batchId, index, failure.type, failure.getMessage(), item, receivedAt);
            } catch (ConflictException e) {
                failed++;
                recordFailure(batchId, index, FailureType.DUPLICATE, e.getMessage(), item, receivedAt);
            } catch (IllegalArgumentException e) {
                failed++;
                recordFailure(batchId, index, FailureType.REJECTED, e.getMessage(), item, receivedAt);
            } catch (DataAccessResourceFailureException | TransientDataAccessException | CannotCreateTransactionException e) {
                // DB 연결 장애처럼 다음 건도 실패할 가능성이 큰 오류는 나머지를 시도하지 않고 요청 전체를 실패시킨다.
                // 이미 커밋된 건은 남고, AI 서버가 다시 보내면 그 건들은 DUPLICATE로 기록된다.
                logger.error("{} batchId={} index={} type=INFRASTRUCTURE processed={} total={} reason={}",
                        FAILURE_LOG_PREFIX, batchId, index, index, items.size(), sanitize(e.getMessage()), e);
                throw e;
            } catch (RuntimeException e) {
                failed++;
                logger.error("{} batchId={} index={} unexpected exception", FAILURE_LOG_PREFIX, batchId, index, e);
                recordFailure(batchId, index, FailureType.UNEXPECTED, e.getClass().getSimpleName() + ": " + e.getMessage(),
                        item, receivedAt);
            }
        }
        long durationMs = java.time.Duration.between(receivedAt, Instant.now()).toMillis();
        if (failed > 0) {
            logger.warn("{} batchId={} total={} created={} failed={} durationMs={}",
                    BATCH_LOG_PREFIX, batchId, items.size(), created.size(), failed, durationMs);
        } else {
            logger.info("{} batchId={} total={} created={} failed=0 durationMs={}",
                    BATCH_LOG_PREFIX, batchId, items.size(), created.size(), durationMs);
        }
        return created;
    }

    private AiCompetitionRequest toRequest(JsonNode item) {
        if (item == null || item.isNull()) {
            throw new ItemFailure(FailureType.INVALID_FORMAT, "공고 항목이 비어 있습니다.");
        }
        if (!item.isObject()) {
            throw new ItemFailure(FailureType.INVALID_FORMAT, "공고 항목은 JSON 객체여야 합니다.");
        }
        AiCompetitionRequest request;
        try {
            request = objectMapper.treeToValue(item, AiCompetitionRequest.class);
        } catch (JacksonException e) {
            throw new ItemFailure(FailureType.INVALID_FORMAT, e.getOriginalMessage());
        }
        Set<ConstraintViolation<AiCompetitionRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ItemFailure(FailureType.INVALID_FIELD, violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining(", ")));
        }
        return request;
    }

    private void recordFailure(String batchId, int index, FailureType type, String message, JsonNode item, Instant receivedAt) {
        String title = text(item, "title");
        String primaryUrl = primaryUrl(item);
        logger.warn("{} batchId={} index={} type={} title={} url={} reason={}", FAILURE_LOG_PREFIX, batchId, index, type,
                sanitize(title), sanitize(primaryUrl), sanitize(message));
        try {
            failureRepository.save(CompetitionIngestionFailure.create(batchId, index, type, message, title, primaryUrl,
                    item == null ? null : item.toString(), receivedAt));
        } catch (RuntimeException e) {
            // 실패 기록 저장이 실패해도 나머지 공고 처리는 계속한다. 위 경고 로그가 마지막 근거로 남는다.
            logger.error("{} batchId={} index={} failure record could not be saved", FAILURE_LOG_PREFIX, batchId, index, e);
        }
    }

    private static String text(JsonNode item, String field) {
        if (item == null || !item.isObject()) return null;
        JsonNode value = item.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static String primaryUrl(JsonNode item) {
        if (item == null || !item.isObject()) return null;
        JsonNode urls = item.get("urls");
        if (urls == null || !urls.isArray()) return null;
        String first = null;
        for (JsonNode url : urls) {
            String value = text(url, "url");
            if (value == null || value.isBlank()) continue;
            JsonNode primary = url.get("primary");
            if (primary != null && primary.isBoolean() && primary.booleanValue()) return value;
            if (first == null) first = value;
        }
        return first;
    }

    /** 외부 입력을 로그 한 줄에 안전하게 남기도록 줄바꿈을 지우고 길이를 제한한다. */
    private static String sanitize(String value) {
        if (value == null) return null;
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ");
        return singleLine.length() <= MAX_LOG_VALUE_LENGTH ? singleLine : singleLine.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }

    private static final class ItemFailure extends RuntimeException {
        private final FailureType type;

        private ItemFailure(FailureType type, String message) {
            super(message, null, false, false);
            this.type = type;
        }
    }
}
