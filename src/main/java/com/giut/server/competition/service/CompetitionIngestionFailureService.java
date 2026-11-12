package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CompetitionIngestionFailureSearchRequest;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureListResponse;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureResponse;
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

@Service
@RequiredArgsConstructor
public class CompetitionIngestionFailureService {

    private final CompetitionIngestionFailureRepository failureRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CompetitionIngestionFailureListResponse getFailures(Long adminUserId, CompetitionIngestionFailureSearchRequest request) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != User.Role.ADMIN || admin.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
        if (request.from() != null && request.to() != null && !request.from().isBefore(request.to())) {
            throw new IllegalArgumentException("from은 to보다 이전 시각이어야 합니다.");
        }

        Specification<CompetitionIngestionFailure> specification = (root, query, builder) -> builder.conjunction();
        if (request.from() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("receivedAt"), request.from()));
        }
        if (request.to() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("receivedAt"), request.to()));
        }
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
}
