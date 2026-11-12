package com.giut.server.competition.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 배치 등록에서 저장하지 못한 공고 한 건. 나머지 공고는 정상 저장되고, 실패한 공고만 원본과 사유를 남긴다.
 */
@Entity
@Table(name = "competition_ingestion_failures", indexes = {
        @Index(name = "idx_ingestion_failures_received_at", columnList = "received_at"),
        @Index(name = "idx_ingestion_failures_batch_id", columnList = "batch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionIngestionFailure {
    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_PAYLOAD_LENGTH = 200_000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 같은 배치 요청으로 들어온 공고를 묶는 식별자(UUID). */
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    /** 배치 요청 안에서의 순서(0부터). */
    @Column(name = "item_index", nullable = false)
    private int itemIndex;

    @Enumerated(EnumType.STRING) @Column(name = "failure_type", nullable = false, length = 30)
    private FailureType failureType;

    @Column(name = "error_message", nullable = false, length = MAX_MESSAGE_LENGTH)
    private String errorMessage;

    @Column(length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "primary_url", columnDefinition = "text")
    private String primaryUrl;

    /** AI 서버가 보낸 해당 공고의 원본 JSON. 원인을 고친 뒤 다시 등록할 때 사용한다. */
    @Column(name = "raw_payload", columnDefinition = "mediumtext")
    private String rawPayload;

    /** 배치 요청을 서버가 받은 시각. 같은 배치의 실패 건은 모두 같은 값이다. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CompetitionIngestionFailure create(String batchId, int itemIndex, FailureType failureType,
                                                     String errorMessage, String title, String primaryUrl,
                                                     String rawPayload, Instant receivedAt) {
        CompetitionIngestionFailure failure = new CompetitionIngestionFailure();
        failure.batchId = batchId;
        failure.itemIndex = itemIndex;
        failure.failureType = failureType;
        failure.errorMessage = truncate(errorMessage == null || errorMessage.isBlank() ? failureType.name() : errorMessage,
                MAX_MESSAGE_LENGTH);
        failure.title = truncate(title, MAX_TITLE_LENGTH);
        failure.primaryUrl = primaryUrl;
        failure.rawPayload = truncate(rawPayload, MAX_PAYLOAD_LENGTH);
        failure.receivedAt = receivedAt;
        return failure;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum FailureType {
        /** JSON을 요청 형식으로 읽지 못함(빈 항목, 모르는 카테고리 코드, 날짜 형식 등). */
        INVALID_FORMAT,
        /** 필수값·길이 등 입력 검증 실패. */
        INVALID_FIELD,
        /** 업무 규칙 위반(부적합 활동, 모집 일정 역전, 대표 URL 규칙 등). */
        REJECTED,
        /** 이미 다른 공모전에 등록된 공식 URL. */
        DUPLICATE,
        /** 위로 분류되지 않은 예외. 서버 로그와 함께 확인한다. */
        UNEXPECTED
    }
}
