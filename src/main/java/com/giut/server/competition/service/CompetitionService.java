package com.giut.server.competition.service;

import com.giut.server.competition.util.CompetitionUrlNormalizer;

import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.dto.request.CompetitionPageRequest;
import com.giut.server.competition.dto.request.AdminCompetitionSearchRequest;
import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.dto.request.CreateCompetitionRequest;
import com.giut.server.competition.dto.request.ReviewCompetitionSubmissionRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.dto.response.AdminCompetitionListResponse;
import com.giut.server.competition.dto.response.CompetitionRecruitmentStatus;
import com.giut.server.competition.dto.response.CompetitionRegistrationSource;
import com.giut.server.competition.dto.response.CompetitionScrapResponse;
import com.giut.server.competition.dto.response.CompetitionTeamResponse;
import com.giut.server.competition.dto.response.CompetitionUrlResponse;
import com.giut.server.competition.dto.response.PublicCompetitionDetailResponse;
import com.giut.server.competition.dto.response.PublicCompetitionListResponse;
import com.giut.server.competition.dto.response.PublicCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionScrap;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.entity.CompetitionVerificationLog;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.competition.repository.CompetitionCreationEvidenceProjection;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.competition.repository.CompetitionScrapSummaryProjection;
import com.giut.server.competition.repository.CompetitionTeamRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionScrapRepository competitionScrapRepository;
    private final CompetitionUrlRepository competitionUrlRepository;
    private final CompetitionVerificationLogRepository competitionVerificationLogRepository;
    private final CompetitionTeamRepository competitionTeamRepository;
    private final UserRepository userRepository;
    private final CompetitionUrlNormalizer competitionUrlNormalizer;
    private final CompetitionListCountCache competitionListCountCache;

    @Transactional(Transactional.TxType.SUPPORTS)
    public PublicCompetitionListResponse getCompetitions(CompetitionSearchRequest request) {
        Instant now = Instant.now();
        Specification<Competition> specification = publishedCompetition().and(
                (root, query, builder) -> builder.greaterThan(root.get("applicationEndAt"), now));

        if (request.normalizedKeyword() != null) {
            specification = specification.and(keywordContains(request.normalizedKeyword()));
        }
        if (request.category() != null) {
            specification = specification.and(hasCategory(request.category()));
        }
        if (request.activityType() != null) {
            specification = specification.and(hasActivityType(request.activityType()));
        }
        if (request.status() != null) {
            specification = specification.and(hasRecruitmentStatus(request.status(), now));
        }

        // 목록은 COUNT 없이 size + 1건만 읽고(Slice), 전체 개수는 따로 구한다.
        Specification<Competition> listSpecification = specification;
        Pageable pageable = PageRequest.of(
                request.pageOrDefault(), request.sizeOrDefault(), request.sortOrDefault().toSort());
        Slice<Competition> slice = competitionRepository.findBy(listSpecification, query -> query.slice(pageable));

        LongSupplier counter = () -> competitionRepository.count(listSpecification);
        LongSupplier totalCounter = request.normalizedKeyword() != null
                ? counter
                : () -> competitionListCountCache.get(new CompetitionListCountCache.Key(
                        request.category(), request.activityType(), request.status()), counter);
        Page<Competition> competitionPage = new PageImpl<>(
                slice.getContent(), pageable, totalElements(slice, pageable, totalCounter));
        List<PublicCompetitionResponse> responses = toPublicResponses(slice.getContent(), now);

        return new PublicCompetitionListResponse(
                responses,
                competitionPage.getNumber(),
                competitionPage.getSize(),
                competitionPage.getTotalElements(),
                competitionPage.getTotalPages(),
                // 캐시된 개수는 늦을 수 있으므로 다음 페이지 여부는 실제로 읽은 결과(size + 1건)로 정한다.
                slice.hasNext()
        );
    }

    /**
     * 공개 목록의 전체 개수. 마지막 페이지처럼 읽은 결과만으로 정확히 알 수 있으면 세지 않는다.
     * 캐시된 개수가 늦어 지금 읽은 결과보다 작으면, 읽은 결과가 보여주는 최소 개수로 올린다.
     */
    private static long totalElements(Slice<?> slice, Pageable pageable, LongSupplier counter) {
        long offset = pageable.getOffset();
        int size = slice.getNumberOfElements();
        if (!slice.hasNext() && (offset == 0 || size > 0)) {
            return offset + size;
        }
        long minimum = size == 0 ? 0 : offset + size + (slice.hasNext() ? 1 : 0);
        return Math.max(counter.getAsLong(), minimum);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PublicCompetitionResponse> getTop10Candidates() {
        Instant now = Instant.now();
        List<Competition> competitions = competitionRepository
                .findTop10ByPopularity(Competition.PublicationStatus.PUBLISHED.name(), now);
        return toPublicResponses(competitions, now);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public PublicCompetitionListResponse getClosingSoonCompetitions(CompetitionPageRequest request) {
        Instant now = Instant.now();
        Instant deadline = now.plus(7, ChronoUnit.DAYS);
        Specification<Competition> specification = publishedCompetition().and((root, query, criteriaBuilder) ->
                criteriaBuilder.and(
                criteriaBuilder.isNotNull(root.get("applicationEndAt")),
                criteriaBuilder.greaterThan(root.get("applicationEndAt"), now),
                criteriaBuilder.lessThanOrEqualTo(root.get("applicationEndAt"), deadline),
                criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("applicationStartAt")),
                        criteriaBuilder.lessThanOrEqualTo(root.get("applicationStartAt"), now)
                )
        )
        );

        if (request.category() != null) {
            specification = specification.and(hasCategory(request.category()));
        }

        Page<Competition> competitions = competitionRepository.findAll(
                specification,
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(),
                        Sort.by(Sort.Direction.ASC, "applicationEndAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PublicCompetitionListResponse(toPublicResponses(competitions.getContent(), now),
                competitions.getNumber(), competitions.getSize(), competitions.getTotalElements(),
                competitions.getTotalPages(), competitions.hasNext());
    }

    @Transactional
    public PublicCompetitionDetailResponse getPublishedCompetition(Long userId, Long competitionId) {
        Instant now = Instant.now();
        // 조회 후 증가가 아니라 UPDATE 한 번으로 조회수 증가와 PUBLISHED 존재 확인을 동시에 처리한다.
        // (동시 조회 시 read-modify-write로 증가분이 유실되는 걸 막는다)
        if (competitionRepository.incrementViewCount(competitionId, Competition.PublicationStatus.PUBLISHED) == 0) {
            throw new ResourceNotFoundException("공개된 공모전을 찾을 수 없습니다.");
        }

        Competition competition = findPublishedCompetition(competitionId);
        CompetitionScrapSummaryProjection scrapSummary =
                competitionScrapRepository.findScrapSummary(userId, competitionId);
        List<CompetitionUrlResponse> urls = competitionUrlRepository.findAllByCompetition_Id(competitionId).stream()
                .map(CompetitionUrlResponse::from)
                .toList();
        List<CompetitionTeamResponse> teams = competitionTeamRepository
                .findWithActiveMemberCount(competitionId, Team.Status.RECRUITING, TeamMember.Status.ACTIVE)
                .stream()
                .map(row -> new CompetitionTeamResponse(
                        row.getTeamId(),
                        row.getName(),
                        row.getLeaderId().equals(userId),
                        row.getDescription(),
                        row.getMaxMemberCount(),
                        row.getActiveMemberCount(),
                        row.getStatus()
                ))
                .toList();
        long recruitingTeamCount = teams.size();
        long teamCount = competitionTeamRepository.countByCompetition_Id(competitionId);

        return PublicCompetitionDetailResponse.from(
                competition,
                recruitmentStatusOf(competition, now),
                scrapSummary.getScrapCount(),
                scrapSummary.isScrapped(),
                urls,
                teamCount,
                recruitingTeamCount,
                teams
        );
    }

    @Transactional
    public CompetitionScrapResponse scrapCompetition(Long userId, Long competitionId) {
        User user = findActiveUserForScrapUpdate(userId);
        Competition competition = findPublishedCompetitionForScrapUpdate(competitionId);

        // 같은 회원의 요청은 회원 행 잠금으로 직렬화돼 있어 확인 뒤 저장해도 중복 행이 생기지 않는다.
        // 행을 실제로 추가했을 때만, 같은 트랜잭션에서 스크랩 수를 1 늘린다(함께 commit·rollback).
        if (!competitionScrapRepository.existsByUser_IdAndCompetition_Id(userId, competitionId)) {
            competitionScrapRepository.save(CompetitionScrap.create(user, competition));
            competitionRepository.incrementScrapCount(competitionId);
        }
        return new CompetitionScrapResponse(competitionId, true);
    }

    @Transactional
    public CompetitionScrapResponse removeCompetitionScrap(Long userId, Long competitionId) {
        findActiveUserForScrapUpdate(userId);
        findPublishedCompetitionForScrapUpdate(competitionId);

        if (competitionScrapRepository.deleteByUserIdAndCompetitionId(userId, competitionId) > 0) {
            competitionRepository.decrementScrapCount(competitionId);
        }
        return new CompetitionScrapResponse(competitionId, false);
    }

    @Transactional
    public AdminCompetitionListResponse getAdminCompetitions(Long adminUserId, AdminCompetitionSearchRequest request) {
        findActiveAdmin(adminUserId);
        // 등록 출처(관리자/AI/미확인)와 무관하게 전체 공모전을 조회하고, 출처는 응답 필드로만 구분한다.
        Specification<Competition> specification = (root, query, criteriaBuilder) ->
                request.publicationStatus() == null
                        ? criteriaBuilder.notEqual(root.get("publicationStatus"), Competition.PublicationStatus.ARCHIVED)
                        : criteriaBuilder.equal(root.get("publicationStatus"), request.publicationStatus());
        if (request.normalizedKeyword() != null) {
            specification = specification.and(keywordContains(request.normalizedKeyword()));
        }
        if (request.category() != null) {
            specification = specification.and(hasCategory(request.category()));
        }
        if (request.activityType() != null) {
            specification = specification.and(hasActivityType(request.activityType()));
        }
        if (request.verificationStatus() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("verificationStatus"), request.verificationStatus()));
        }
        Page<Competition> page = competitionRepository.findAll(specification,
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = page.getContent().stream().map(Competition::getId).toList();
        Map<Long, List<CompetitionUrlResponse>> urlsById = ids.isEmpty() ? Map.of()
                : competitionUrlRepository.findAllByCompetition_IdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(url -> url.getCompetition().getId(),
                        Collectors.mapping(CompetitionUrlResponse::from, Collectors.toList())));
        Map<Long, CompetitionRegistrationSource> sourcesById = resolveRegistrationSources(ids);
        List<AdminCompetitionResponse> responses = page.getContent().stream()
                .map(competition -> AdminCompetitionResponse.from(competition,
                        sourcesById.get(competition.getId()),
                        urlsById.getOrDefault(competition.getId(), List.of())))
                .toList();
        return new AdminCompetitionListResponse(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @Transactional
    public AdminCompetitionResponse createByAdmin(Long adminUserId, CreateCompetitionRequest input) {
        User admin = findActiveAdmin(adminUserId);
        UpsertCompetitionRequest request = input.toUpsertRequest();
        // 공개는 관리자 승인으로만 이뤄진다. DRAFT로 등록하면 회원 제출·AI 등록과 같은 검토 대기가 되어 /review로 공개한다.
        // 그 밖의 상태(바로 공개 등)는 관리자가 내용을 보고 직접 결정한 것으로 보고 기존처럼 수동 검증으로 저장한다.
        Competition.VerificationStatus verificationStatus =
                request.publicationStatus() == Competition.PublicationStatus.DRAFT
                        ? Competition.VerificationStatus.MANUAL_REVIEW_PENDING
                        : Competition.VerificationStatus.MANUALLY_VERIFIED;
        validateRequest(request);
        List<NormalizedCompetitionUrl> urls = normalizeUrls(request.urls());
        validateUrlOwnership(urls, null);

        Competition competition = Competition.create(
                request.title().trim(),
                request.category(),
                trimToNull(request.hostOrganization()),
                trimToNull(request.targetParticipants()),
                request.summary().trim(),
                request.applicationStartAt(),
                request.applicationEndAt(),
                request.publicationStatus(),
                verificationStatus,
                admin
        );
        if (request.activityType() != null) {
            competition.changeActivityType(request.activityType());
        }
        competition.changePrizeInfo(trimToNull(request.prizeInfo()));
        competition.changeTotalPrizeAmount(request.totalPrizeAmount());
        competition = competitionRepository.save(competition);

        List<CompetitionUrl> savedUrls = saveUrls(competition, urls);
        competitionVerificationLogRepository.save(CompetitionVerificationLog.createAdminReviewLog(
                competition,
                null,
                verificationStatus,
                admin,
                CompetitionVerificationLog.ADMIN_CREATION_REASON
        ));

        return toResponse(competition, CompetitionRegistrationSource.ADMIN, savedUrls);
    }

    @Transactional
    public AdminCompetitionResponse createByAi(AiCompetitionRequest input) {
        Competition.ActivityType activityType = input.activityType().toActivityType();
        UpsertCompetitionRequest request = new UpsertCompetitionRequest(
                input.title(), input.primaryCategory(), input.hostOrganization(), input.targetParticipants().name(), input.summary(),
                input.applicationStartAt(), input.applicationEndAt(), Competition.PublicationStatus.DRAFT, input.urls());
        validateRequest(request);
        List<NormalizedCompetitionUrl> urls = normalizeUrls(request.urls());
        validateUrlOwnership(urls, null);
        Competition competition = Competition.create(
                request.title().trim(), request.category(), trimToNull(request.hostOrganization()),
                request.targetParticipants().trim(), trimToNull(request.summary()), request.applicationStartAt(),
                // AI가 수집한 공고는 바로 공개하지 않는다. 관리자가 /review로 하나씩 승인해야 공개된다.
                request.applicationEndAt(), Competition.PublicationStatus.DRAFT,
                Competition.VerificationStatus.MANUAL_REVIEW_PENDING, null);
        competition.changeActivityType(activityType);
        competition.changePrizeInfo(trimToNull(input.prizeInfo()));
        competition.changeTotalPrizeAmount(input.totalPrizeAmount());
        competition.changeIsForEnrolledOnly(input.isForEnrolledOnly());
        competition.changeIsTeam(input.isTeam());
        competition = competitionRepository.save(competition);
        List<CompetitionUrl> savedUrls = saveUrls(competition, urls);
        competitionVerificationLogRepository.save(CompetitionVerificationLog.createAiVerificationLog(competition));
        return toResponse(competition, CompetitionRegistrationSource.AI, savedUrls);
    }

    @Transactional
    public Long createByMember(Long userId, String sourceUrl, CompetitionUrlNormalizer.NormalizedUrl source,
                               CreateCompetitionRequest input, Boolean isForEnrolledOnly, Boolean isTeam) {
        // 작업자는 HTTP 인증 필터를 거치지 않는다. 탈퇴·학교 인증 해제와 같은 행 잠금 안에서 자격을 재확인한다.
        User submitter = userRepository.findByIdForUpdate(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("활성 상태의 사용자만 공모전을 제출할 수 있습니다."));
        if (submitter.getRole() != User.Role.ADMIN && submitter.getUniversityVerifiedAt() == null) {
            throw new ForbiddenException("학교 인증을 완료한 사용자만 공모전을 제출할 수 있습니다.");
        }
        UpsertCompetitionRequest request = input.toUpsertRequest();
        validateRequest(request);
        List<NormalizedCompetitionUrl> urls = normalizeUrls(request.urls());
        validateUrlOwnership(urls, null);

        Competition competition = Competition.create(request.title().trim(), request.category(),
                trimToNull(request.hostOrganization()), trimToNull(request.targetParticipants()),
                request.summary().trim(), request.applicationStartAt(), request.applicationEndAt(),
                Competition.PublicationStatus.DRAFT, Competition.VerificationStatus.MANUAL_REVIEW_PENDING, null);
        competition.recordSubmission(submitter, sourceUrl, source.hash());
        if (request.activityType() != null) {
            competition.changeActivityType(request.activityType());
        }
        competition.changePrizeInfo(trimToNull(request.prizeInfo()));
        competition.changeTotalPrizeAmount(request.totalPrizeAmount());
        competition.changeIsForEnrolledOnly(isForEnrolledOnly);
        competition.changeIsTeam(isTeam);
        competition = competitionRepository.saveAndFlush(competition);
        saveUrls(competition, urls);
        return competition.getId();
    }

    @Transactional
    public AdminCompetitionResponse reviewSubmission(Long adminUserId, Long competitionId,
                                                      ReviewCompetitionSubmissionRequest request) {
        User admin = findActiveAdmin(adminUserId);
        Competition competition = findEditableCompetitionForUpdate(competitionId);
        Competition.VerificationStatus previousStatus = competition.getVerificationStatus();
        // 회원 제출·AI 등록·관리자 DRAFT 등록 모두 같은 검토로 공개하거나 반려한다. 이미 공개·반려된 건은 다시 처리하지 않는다.
        if (competition.getPublicationStatus() != Competition.PublicationStatus.DRAFT) {
            throw new ConflictException("공개 대기(DRAFT) 중인 공모전만 검토할 수 있습니다.");
        }

        boolean approved = request.decision() == ReviewCompetitionSubmissionRequest.Decision.APPROVE;
        String reason = trimToNull(request.reason());
        if (!approved && reason == null) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        competition.reviewSubmission(approved, admin);
        competitionVerificationLogRepository.save(CompetitionVerificationLog.createAdminReviewLog(
                competition, previousStatus, competition.getVerificationStatus(), admin, reason));
        return toResponse(competition, competitionUrlRepository.findAllByCompetition_Id(competitionId));
    }

    @Transactional
    public AdminCompetitionResponse updateByAdmin(Long adminUserId, Long competitionId, UpsertCompetitionRequest request) {
        User admin = findActiveAdmin(adminUserId);
        Competition competition = findEditableCompetitionForUpdate(competitionId);
        // 공개 상태는 수정 API로 바꾸지 않는다. DRAFT → PUBLISHED·REJECTED는 검토 API(/review), 보관은 삭제 API로만 한다.
        // 그래서 검토를 건너뛴 공개, 반려 후 DRAFT로 되돌려 다시 검토받기, 공개 건을 DRAFT로 내리기가 모두 막힌다.
        if (request.publicationStatus() != competition.getPublicationStatus()) {
            throw new ConflictException("공개 상태는 수정 API로 바꿀 수 없습니다. 공개·반려는 검토 API, 보관은 삭제 API를 사용하세요.");
        }
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
        if (request.activityType() != null) {
            competition.changeActivityType(request.activityType());
        }
        if (request.prizeInfo() != null) {
            competition.changePrizeInfo(trimToNull(request.prizeInfo()));
        }

        if (request.totalPrizeAmount() != null) {
            competition.changeTotalPrizeAmount(request.totalPrizeAmount());
        }

        // derived delete는 다음 flush까지 실제 DELETE가 지연되는데, 뒤이은 saveUrls의 INSERT와
        // 순서가 뒤섞이면 안 되므로(예: 같은 공식 URL unique 제약) 여기서 flush로 삭제를 먼저 확정한다.
        competitionUrlRepository.deleteByCompetition_Id(competitionId);
        competitionUrlRepository.flush();
        List<CompetitionUrl> savedUrls = saveUrls(competition, urls);

        return toResponse(competition, savedUrls);
    }

    @Transactional
    public void deleteByAdmin(Long adminUserId, Long competitionId) {
        findActiveAdmin(adminUserId);
        if (competitionId == null || competitionId <= 0) {
            throw new IllegalArgumentException("공모전 ID는 양수여야 합니다.");
        }
        Competition competition = competitionRepository.findByIdForUpdate(competitionId)
                .orElseThrow(() -> new ResourceNotFoundException("공모전을 찾을 수 없습니다."));
        competition.archive();
    }

    private Competition findEditableCompetitionForUpdate(Long competitionId) {
        // 수정·발행·삭제가 전부 이 메서드를 거치므로, 같은 공모전에 대한 관리자 작업끼리
        // 비관적 락으로 직렬화해 한쪽이 보던 상태가 다른 쪽 커밋으로 덮어써지지 않게 한다.
        return competitionRepository.findByIdForUpdate(competitionId)
                .filter(competition -> competition.getPublicationStatus() != Competition.PublicationStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("공모전을 찾을 수 없습니다."));
    }

    private User findActiveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (admin.getRole() != User.Role.ADMIN || admin.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
        return admin;
    }

    private User findActiveUserForScrapUpdate(Long userId) {
        // Share the same lock for adding and removing, including the first insert.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new ForbiddenException("활성 상태의 사용자만 공모전을 스크랩할 수 있습니다.");
        }
        return user;
    }

    /**
     * 스크랩 추가·취소용. 공모전 행을 처음부터 배타 잠금으로 읽는다. 잠금 없이 읽으면 스크랩 행 INSERT의 외래 키 검사가
     * 공모전 행에 공유 잠금을 걸고, 뒤이은 scrap_count UPDATE가 배타 잠금으로 올리려다 다른 회원의 같은 시도와 교착된다
     * (실제 MySQL에서 재현). 잠금 순서는 항상 회원 행 → 공모전 행이다.
     */
    private Competition findPublishedCompetitionForScrapUpdate(Long competitionId) {
        return competitionRepository.findByIdForUpdate(competitionId)
                .filter(competition -> competition.getPublicationStatus() == Competition.PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("공개된 공모전을 찾을 수 없습니다."));
    }

    private Competition findPublishedCompetition(Long competitionId) {
        return competitionRepository.findByIdAndPublicationStatus(
                        competitionId,
                        Competition.PublicationStatus.PUBLISHED
                )
                .orElseThrow(() -> new ResourceNotFoundException("공개된 공모전을 찾을 수 없습니다."));
    }

    private Map<Long, CompetitionRegistrationSource> resolveRegistrationSources(List<Long> competitionIds) {
        // Competition에는 생성 출처 컬럼이 없어, 생성 시 함께 남긴 최초 검증 로그로 역판별한다.
        // 근거가 없거나(레거시·수동 삽입) 관리자/AI 근거가 동시에 있으면 추측하지 않고 UNKNOWN으로 둔다.
        Map<Long, CompetitionRegistrationSource> sources = new HashMap<>();
        competitionIds.forEach(id -> sources.put(id, CompetitionRegistrationSource.UNKNOWN));
        if (competitionIds.isEmpty()) {
            return sources;
        }
        Map<Long, Set<CompetitionVerificationLog.Method>> methodsById = competitionVerificationLogRepository
                .findCreationEvidence(competitionIds).stream()
                .collect(Collectors.groupingBy(CompetitionCreationEvidenceProjection::getCompetitionId,
                        Collectors.mapping(CompetitionCreationEvidenceProjection::getMethod, Collectors.toSet())));
        methodsById.forEach((id, methods) -> {
            if (methods.size() != 1) {
                return;
            }
            sources.put(id, methods.contains(CompetitionVerificationLog.Method.ADMIN_REVIEW)
                    ? CompetitionRegistrationSource.ADMIN
                    : CompetitionRegistrationSource.AI);
        });
        return sources;
    }

    private Specification<Competition> publishedCompetition() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("publicationStatus"),
                Competition.PublicationStatus.PUBLISHED
        );
    }

    private Specification<Competition> keywordContains(String keyword) {
        // LOWER() 적용 + 선행 와일드카드(%...)라 인덱스를 못 타는 전체 테이블 스캔이다.
        // 데이터가 늘수록 느려짐이 측정으로 확인된 지점(실측: 5만건 28ms → 20만건 101ms).
        return (root, query, criteriaBuilder) -> {
            String escapedKeyword = keyword.toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            String searchKeyword = "%" + escapedKeyword + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), searchKeyword, '!'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("hostOrganization")), searchKeyword, '!')
            );
        };
    }

    private Specification<Competition> hasCategory(Competition.Category category) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("category"), category);
    }

    private Specification<Competition> hasActivityType(Competition.ActivityType activityType) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("activityType"), activityType);
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

    private List<PublicCompetitionResponse> toPublicResponses(List<Competition> competitions, Instant now) {
        Map<Long, String> primaryUrlByCompetitionId = findPrimaryUrls(competitions);
        Map<Long, Long> scrapCountByCompetitionId = findScrapCounts(competitions);
        Map<Long, Long> teamCountByCompetitionId = findTeamCounts(competitions);
        return competitions.stream()
                .map(competition -> PublicCompetitionResponse.from(
                        competition,
                        recruitmentStatusOf(competition, now),
                        primaryUrlByCompetitionId.get(competition.getId()),
                        scrapCountByCompetitionId.getOrDefault(competition.getId(), 0L),
                        teamCountByCompetitionId.getOrDefault(competition.getId(), 0L)
                ))
                .toList();
    }

    private Map<Long, Long> findScrapCounts(List<Competition> competitions) {
        if (competitions.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> scrapCountByCompetitionId = new HashMap<>();
        competitionScrapRepository.countByCompetitionIdIn(
                        competitions.stream().map(Competition::getId).toList()
                )
                .forEach(count -> scrapCountByCompetitionId.put(
                        count.getCompetitionId(),
                        count.getScrapCount()
                ));
        return scrapCountByCompetitionId;
    }

    private Map<Long, Long> findTeamCounts(List<Competition> competitions) {
        if (competitions.isEmpty()) {
            return Map.of();
        }
        List<Long> competitionIds = competitions.stream().map(Competition::getId).toList();

        Map<Long, Long> teamCountByCompetitionId = new HashMap<>();
        competitionTeamRepository.countByCompetitionIdsAndStatus(
                        competitionIds, Team.Status.RECRUITING)
                .forEach(count -> teamCountByCompetitionId.put(
                        count.getCompetitionId(),
                        count.getTeamCount()
                ));
        return teamCountByCompetitionId;
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
        if (request.applicationEndAt() == null) {
            throw new IllegalArgumentException("모집 마감 일시는 필수입니다.");
        }
        if (request.applicationStartAt() != null
                && request.applicationEndAt() != null
                && !request.applicationEndAt().isAfter(request.applicationStartAt())) {
            throw new IllegalArgumentException("모집 마감 일시는 시작 일시보다 늦어야 합니다.");
        }
        long primaryUrlCount = request.urls().stream()
                .filter(url -> Boolean.TRUE.equals(url.primary()))
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
                            Boolean.TRUE.equals(request.primary())
                    );
                })
                .toList();
    }

    private void validateUrlOwnership(List<NormalizedCompetitionUrl> urls, Long currentCompetitionId) {
        if (urls.isEmpty()) {
            return;
        }
        List<String> hashes = urls.stream().filter(url -> url.type() == CompetitionUrl.Type.OFFICIAL)
                .map(NormalizedCompetitionUrl::hash).toList();
        if (hashes.isEmpty()) return;
        boolean ownedByAnotherCompetition = competitionUrlRepository
                .findAllByOfficialUrlHashIn(hashes).stream()
                .anyMatch(existingUrl -> currentCompetitionId == null
                        || !existingUrl.getCompetition().getId().equals(currentCompetitionId));
        if (ownedByAnotherCompetition) {
            throw new ConflictException("이미 다른 공모전에 등록된 공식 URL입니다.");
        }
    }

    private List<CompetitionUrl> saveUrls(Competition competition, List<NormalizedCompetitionUrl> urls) {
        try {
            return competitionUrlRepository.saveAllAndFlush(urls.stream()
                .map(url -> CompetitionUrl.create(
                        competition,
                        url.type(),
                        url.originalUrl(),
                        url.normalizedUrl(),
                        url.hash(),
                        url.primary()
                ))
                .toList());
        } catch (DataIntegrityViolationException e) {
            // validateUrlOwnership의 사전 검사는 SELECT 후 INSERT라 그 사이 경쟁 상태가 가능하다.
            // DB unique 제약 위반을 여기서 잡아 동시 등록 레이스에 대한 최종 방어선으로 삼는다.
            Throwable cause = e.getMostSpecificCause();
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_competition_urls_official_hash")) {
                throw new ConflictException("이미 다른 공모전에 등록된 공식 URL입니다.", e);
            }
            throw e;
        }
    }

    private AdminCompetitionResponse toResponse(Competition competition, List<CompetitionUrl> urls) {
        return toResponse(competition,
                resolveRegistrationSources(List.of(competition.getId())).get(competition.getId()), urls);
    }

    private AdminCompetitionResponse toResponse(Competition competition, CompetitionRegistrationSource registrationSource,
                                                List<CompetitionUrl> urls) {
        return AdminCompetitionResponse.from(
                competition,
                registrationSource,
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
