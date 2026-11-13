package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CompetitionIngestionFailureSearchRequest;
import com.giut.server.competition.dto.request.CompetitionIngestionFailureSummaryRequest;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureListResponse;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureResponse;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureSummaryResponse;
import com.giut.server.competition.entity.CompetitionIngestionFailure;
import com.giut.server.competition.repository.CompetitionIngestionFailureRepository;
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

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionIngestionFailureService {

    private final CompetitionIngestionFailureRepository failureRepository;
    private final UserRepository userRepository;

    /** 기간 안의 실패를 유형별로 센다. 중복(DUPLICATE)이 얼마나 들어오는지 보는 용도다. */
    @Transactional(readOnly = true)
    public CompetitionIngestionFailureSummaryResponse getSummary(Long adminUserId,
                                                                 CompetitionIngestionFailureSummaryRequest request) {
        requireActiveAdmin(adminUserId);
        Specification<CompetitionIngestionFailure> period = receivedBetween(request.from(), request.to());
        Map<CompetitionIngestionFailure.FailureType, Long> byType = new EnumMap<>(CompetitionIngestionFailure.FailureType.class);
        long total = 0;
        for (CompetitionIngestionFailure.FailureType type : CompetitionIngestionFailure.FailureType.values()) {
            long count = failureRepository.count(period.and((root, query, builder) ->
                    builder.equal(root.get("failureType"), type)));
            byType.put(type, count);
            total += count;
        }
        return new CompetitionIngestionFailureSummaryResponse(request.from(), request.to(), total, byType);
    }

    @Transactional(readOnly = true)
    public CompetitionIngestionFailureListResponse getFailures(Long adminUserId, CompetitionIngestionFailureSearchRequest request) {
        requireActiveAdmin(adminUserId);

        Specification<CompetitionIngestionFailure> specification = receivedBetween(request.from(), request.to());
        if (request.failureType() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("failureType"), request.failureType()));
        }
        if (request.normalizedBatchId() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("batchId"), request.normalizedBatchId()));
        }

        Page<CompetitionIngestionFailure> page = failureRepository.findAll(specification,
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(),
                        Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.ASC, "itemIndex"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new CompetitionIngestionFailureListResponse(
                page.getContent().stream().map(CompetitionIngestionFailureResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private void requireActiveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != User.Role.ADMIN || admin.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
    }

    /** receivedAt이 from 이상 to 미만인 기록. from·to 중 생략한 쪽은 제한하지 않는다. */
    private Specification<CompetitionIngestionFailure> receivedBetween(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from은 to보다 이전 시각이어야 합니다.");
        }
        Specification<CompetitionIngestionFailure> specification = (root, query, builder) -> builder.conjunction();
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("receivedAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("receivedAt"), to));
        }
        return specification;
    }
}
