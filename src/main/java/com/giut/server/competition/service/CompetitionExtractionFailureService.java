package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CompetitionExtractionFailureSearchRequest;
import com.giut.server.competition.dto.response.CompetitionExtractionFailureListResponse;
import com.giut.server.competition.dto.response.CompetitionExtractionFailureResponse;
import com.giut.server.competition.entity.CompetitionExtractionJob;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.competition.repository.CompetitionExtractionJobSubscriptionRepository;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 최종 실패(FAILED)한 회원 URL 추출 작업을 관리자가 확인하는 조회. 실패 작업은 {@code failure-retention} 동안만 남는다. */
@Service
@RequiredArgsConstructor
public class CompetitionExtractionFailureService {
    private final CompetitionExtractionJobRepository jobs;
    private final CompetitionExtractionJobSubscriptionRepository subscriptions;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public CompetitionExtractionFailureListResponse getFailures(Long adminUserId,
                                                                CompetitionExtractionFailureSearchRequest request) {
        User admin = users.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != User.Role.ADMIN || admin.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
        if (request.from() != null && request.to() != null && !request.from().isBefore(request.to())) {
            throw new IllegalArgumentException("from은 to보다 이전 시각이어야 합니다.");
        }

        Specification<CompetitionExtractionJob> specification = (root, query, builder) ->
                builder.equal(root.get("status"), CompetitionExtractionJob.Status.FAILED);
        if (request.from() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("completedAt"), request.from()));
        }
        if (request.to() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("completedAt"), request.to()));
        }
        if (request.normalizedErrorCode() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("errorCode"), request.normalizedErrorCode()));
        }

        Page<CompetitionExtractionJob> page = jobs.findAll(specification,
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(),
                        Sort.by(Sort.Direction.DESC, "completedAt").and(Sort.by(Sort.Direction.DESC, "id"))));

        Map<Long, Long> waiting = new HashMap<>();
        List<Long> ids = page.getContent().stream().map(CompetitionExtractionJob::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : subscriptions.countByJobIds(ids)) {
                waiting.put((Long) row[0], (Long) row[1]);
            }
        }
        return new CompetitionExtractionFailureListResponse(
                page.getContent().stream()
                        .map(job -> CompetitionExtractionFailureResponse.from(job, waiting.getOrDefault(job.getId(), 0L)))
                        .toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
