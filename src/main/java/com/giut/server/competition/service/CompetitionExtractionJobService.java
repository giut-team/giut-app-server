package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.CompetitionExtractionResult;
import com.giut.server.competition.dto.response.CompetitionExtractionJobResponse;
import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.competition.util.PublicCompetitionSource;
import com.giut.server.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

/**
 * 회원 URL 추출의 비동기 접수·조회. 입력 정규화 → 기존 링크 판단 → 공개 주소 검증 순서로 확인한다.
 * 이 클래스는 트랜잭션을 열지 않는다. DNS 확인은 트랜잭션 밖에서 하고, 저장은 {@link CompetitionExtractionJobStore}가 짧게 commit한다.
 */
@Service
@RequiredArgsConstructor
public class CompetitionExtractionJobService {
    private final CompetitionUrlNormalizer urlNormalizer;
    private final PublicCompetitionSource publicSource;
    private final AiCompetitionExtractClient extractor;
    private final CompetitionSubmissionService submissions;
    private final CompetitionExtractionJobStore store;
    private final CompetitionExtractionJobRepository jobs;
    private final ObjectMapper objectMapper;

    public Accepted accept(Long userId, String rawUrl) {
        String sourceUrl = rawUrl.trim();
        CompetitionUrlNormalizer.NormalizedUrl source = urlNormalizer.normalize(sourceUrl);
        Optional<CompetitionSubmissionService.ExistingCompetition> existing = submissions.findExisting(source.hash());
        if (existing.isPresent()) {
            return new Accepted(CompetitionExtractionJobResponse.existing(
                    existing.get().url(), existing.get().competitionId()), false);
        }

        publicSource.requirePublicAddress(source.value());
        if (!extractor.isConfigured()) {
            throw new AiCompetitionExtractClient.ExtractionUnavailable(HttpStatus.SERVICE_UNAVAILABLE,
                    "공모전 추출 서비스가 설정되지 않았습니다.");
        }

        Instant now = Instant.now();
        CompetitionExtractionJobStore.Enqueued enqueued;
        try {
            enqueued = store.enqueue(userId, sourceUrl, source.hash(), now);
        } catch (DataIntegrityViolationException exception) {
            // 같은 URL의 진행 중 작업이 동시에 만들어진 경우(active_key UNIQUE). 먼저 commit된 작업에 합류한다.
            enqueued = store.join(userId, source.hash(), now).orElseThrow(() -> exception);
        }
        return new Accepted(toResponse(enqueued.job()), enqueued.created());
    }

    /** 접수했거나 같은 URL로 합류한 작업만 조회한다. 그 밖의 작업 ID는 존재 여부를 드러내지 않도록 404로 같게 처리한다. */
    public CompetitionExtractionJobResponse get(Long userId, Long jobId) {
        return jobs.findAccessible(jobId, userId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("추출 작업을 찾을 수 없습니다."));
    }

    private CompetitionExtractionJobResponse toResponse(CompetitionExtractionJob job) {
        CompetitionExtractionResult result = null;
        if (job.getStatus() == CompetitionExtractionJob.Status.SUCCEEDED) {
            AiCompetitionExtractClient.Extraction extraction =
                    objectMapper.readValue(job.getResultJson(), AiCompetitionExtractClient.Extraction.class);
            result = submissions.toExtractionResult(job.getSourceUrl(), job.getSourceUrlHash(), extraction);
        }
        return CompetitionExtractionJobResponse.of(job, result);
    }

    /** created=true면 이번 요청으로 새 작업이 commit됐다. */
    public record Accepted(CompetitionExtractionJobResponse response, boolean created) {
    }
}
