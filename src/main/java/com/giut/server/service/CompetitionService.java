package com.giut.server.service;

import com.giut.server.dto.competition.request.CompetitionUrlRequest;
import com.giut.server.dto.competition.request.CompetitionSearchRequest;
import com.giut.server.dto.competition.request.UpsertCompetitionRequest;
import com.giut.server.dto.competition.response.AdminCompetitionResponse;
import com.giut.server.dto.competition.response.CompetitionRecruitmentStatus;
import com.giut.server.dto.competition.response.CompetitionUrlResponse;
import com.giut.server.dto.competition.response.PublicCompetitionListResponse;
import com.giut.server.dto.competition.response.PublicCompetitionResponse;
import com.giut.server.entity.Competition;
import com.giut.server.entity.CompetitionUrl;
import com.giut.server.entity.CompetitionVerificationLog;
import com.giut.server.entity.User;
import com.giut.server.exception.ConflictException;
import com.giut.server.exception.ForbiddenException;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.CompetitionRepository;
import com.giut.server.repository.CompetitionUrlRepository;
import com.giut.server.repository.CompetitionVerificationLogRepository;
import com.giut.server.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionUrlRepository competitionUrlRepository;
    private final CompetitionVerificationLogRepository competitionVerificationLogRepository;
    private final UserRepository userRepository;
    private final CompetitionUrlNormalizer competitionUrlNormalizer;

    @Transactional(Transactional.TxType.SUPPORTS)
    public PublicCompetitionListResponse getPublishedCompetitions(CompetitionSearchRequest request) {
        Instant now = Instant.now();
        Specification<Competition> specification = publishedCompetition();

        if (request.normalizedKeyword() != null) {
            specification = specification.and(keywordContains(request.normalizedKeyword()));
        }
        if (request.category() != null) {
            specification = specification.and(hasCategory(request.category()));
        }
        if (request.status() != null) {
            specification = specification.and(hasRecruitmentStatus(request.status(), now));
        }

        Page<Competition> competitionPage = competitionRepository.findAll(
                specification,
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), Sort.by(Sort.Direction.DESC, "id"))
        );
        List<Competition> competitions = competitionPage.getContent();
        Map<Long, String> primaryUrlByCompetitionId = findPrimaryUrls(competitions);

        List<PublicCompetitionResponse> responses = competitions.stream()
                .map(competition -> PublicCompetitionResponse.from(
                        competition,
                        recruitmentStatusOf(competition, now),
                        primaryUrlByCompetitionId.get(competition.getId())
                ))
                .toList();

        return new PublicCompetitionListResponse(
                responses,
                competitionPage.getNumber(),
                competitionPage.getSize(),
                competitionPage.getTotalElements(),
                competitionPage.getTotalPages(),
                competitionPage.hasNext()
        );
    }

    @Transactional
    public AdminCompetitionResponse createByAdmin(Long adminUserId, UpsertCompetitionRequest request) {
        User admin = findActiveAdmin(adminUserId);
        validateRequest(request);
        List<NormalizedCompetitionUrl> urls = normalizeUrls(request.urls());
        validateUrlOwnership(urls, null);

        Competition competition = competitionRepository.save(Competition.create(
                request.title().trim(),
                request.category(),
                trimToNull(request.hostOrganization()),
                trimToNull(request.targetParticipants()),
                request.summary().trim(),
                request.applicationStartAt(),
                request.applicationEndAt(),
                request.publicationStatus(),
                Competition.VerificationStatus.MANUALLY_VERIFIED,
                admin
        ));

        List<CompetitionUrl> savedUrls = saveUrls(competition, urls);
        competitionVerificationLogRepository.save(CompetitionVerificationLog.createAdminReviewLog(
                competition,
                null,
                Competition.VerificationStatus.MANUALLY_VERIFIED,
                admin,
                "관리자 직접 등록"
        ));

        return toResponse(competition, savedUrls);
    }

    @Transactional
    public AdminCompetitionResponse updateByAdmin(Long adminUserId, Long competitionId, UpsertCompetitionRequest request) {
        User admin = findActiveAdmin(adminUserId);
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("공모전을 찾을 수 없습니다."));
        validateRequest(request);
        List<NormalizedCompetitionUrl> urls = normalizeUrls(request.urls());
        validateUrlOwnership(urls, competitionId);

        competition.update(
                request.title().trim(),
                request.category(),
                trimToNull(request.hostOrganization()),
                trimToNull(request.targetParticipants()),
                request.summary().trim(),
                request.applicationStartAt(),
                request.applicationEndAt(),
                request.publicationStatus(),
                competition.getVerificationStatus(),
                admin
        );

        competitionUrlRepository.deleteByCompetition_Id(competitionId);
        competitionUrlRepository.flush();
        List<CompetitionUrl> savedUrls = saveUrls(competition, urls);

        return toResponse(competition, savedUrls);
    }

    private User findActiveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != User.Role.ADMIN || admin.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
        return admin;
    }

    private Specification<Competition> publishedCompetition() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("publicationStatus"),
                Competition.PublicationStatus.PUBLISHED
        );
    }

    private Specification<Competition> keywordContains(String keyword) {
        return (root, query, criteriaBuilder) -> {
            String searchKeyword = "%" + keyword.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), searchKeyword),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("hostOrganization")), searchKeyword)
            );
        };
    }

    private Specification<Competition> hasCategory(Competition.Category category) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("category"), category);
    }

    private Specification<Competition> hasRecruitmentStatus(
            CompetitionRecruitmentStatus status,
            Instant now
    ) {
        return (root, query, criteriaBuilder) -> switch (status) {
            case UPCOMING -> criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get("applicationStartAt")),
                    criteriaBuilder.greaterThan(root.get("applicationStartAt"), now)
            );
            case OPEN -> criteriaBuilder.and(
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("applicationStartAt")),
                            criteriaBuilder.lessThanOrEqualTo(root.get("applicationStartAt"), now)
                    ),
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("applicationEndAt")),
                            criteriaBuilder.greaterThan(root.get("applicationEndAt"), now)
                    )
            );
            case CLOSED -> criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get("applicationEndAt")),
                    criteriaBuilder.lessThanOrEqualTo(root.get("applicationEndAt"), now)
            );
        };
    }

    private Map<Long, String> findPrimaryUrls(List<Competition> competitions) {
        if (competitions.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> primaryUrlByCompetitionId = new HashMap<>();
        competitionUrlRepository.findAllByCompetition_IdInAndPrimaryTrue(
                        competitions.stream().map(Competition::getId).toList()
                )
                .forEach(url -> primaryUrlByCompetitionId.put(url.getCompetition().getId(), url.getUrl()));
        return primaryUrlByCompetitionId;
    }

    private CompetitionRecruitmentStatus recruitmentStatusOf(Competition competition, Instant now) {
        if (competition.getApplicationStartAt() != null && competition.getApplicationStartAt().isAfter(now)) {
            return CompetitionRecruitmentStatus.UPCOMING;
        }
        if (competition.getApplicationEndAt() != null && !competition.getApplicationEndAt().isAfter(now)) {
            return CompetitionRecruitmentStatus.CLOSED;
        }
        return CompetitionRecruitmentStatus.OPEN;
    }

    private void validateRequest(UpsertCompetitionRequest request) {
        if (request.applicationStartAt() != null
                && request.applicationEndAt() != null
                && !request.applicationEndAt().isAfter(request.applicationStartAt())) {
            throw new IllegalArgumentException("모집 마감 일시는 시작 일시보다 늦어야 합니다.");
        }
        long primaryUrlCount = request.urls().stream()
                .filter(CompetitionUrlRequest::primary)
                .count();
        if (primaryUrlCount != 1) {
            throw new IllegalArgumentException("대표 URL은 정확히 하나여야 합니다.");
        }
    }

    private List<NormalizedCompetitionUrl> normalizeUrls(List<CompetitionUrlRequest> urlRequests) {
        Set<String> hashes = new HashSet<>();
        return urlRequests.stream()
                .map(request -> {
                    CompetitionUrlNormalizer.NormalizedUrl normalized = competitionUrlNormalizer.normalize(request.url());
                    if (!hashes.add(normalized.hash())) {
                        throw new IllegalArgumentException("같은 URL을 중복 등록할 수 없습니다.");
                    }
                    return new NormalizedCompetitionUrl(
                            request.type(),
                            request.url().trim(),
                            normalized.value(),
                            normalized.hash(),
                            request.primary()
                    );
                })
                .toList();
    }

    private void validateUrlOwnership(List<NormalizedCompetitionUrl> urls, Long currentCompetitionId) {
        for (NormalizedCompetitionUrl url : urls) {
            competitionUrlRepository.findByNormalizedUrlHash(url.hash())
                    .filter(existingUrl -> currentCompetitionId == null
                            || !existingUrl.getCompetition().getId().equals(currentCompetitionId))
                    .ifPresent(existingUrl -> {
                        throw new ConflictException("이미 다른 공모전에 등록된 URL입니다.");
                    });
        }
    }

    private List<CompetitionUrl> saveUrls(Competition competition, List<NormalizedCompetitionUrl> urls) {
        return competitionUrlRepository.saveAll(urls.stream()
                .map(url -> CompetitionUrl.create(
                        competition,
                        url.type(),
                        url.originalUrl(),
                        url.normalizedUrl(),
                        url.hash(),
                        url.primary()
                ))
                .toList());
    }

    private AdminCompetitionResponse toResponse(Competition competition, List<CompetitionUrl> urls) {
        return AdminCompetitionResponse.from(
                competition,
                urls.stream().map(CompetitionUrlResponse::from).toList()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record NormalizedCompetitionUrl(
            CompetitionUrl.Type type,
            String originalUrl,
            String normalizedUrl,
            String hash,
            boolean primary
    ) {
    }
}
