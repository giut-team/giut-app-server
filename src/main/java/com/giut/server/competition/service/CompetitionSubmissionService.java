package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.AiActivityType;
import com.giut.server.competition.dto.request.AiCompetitionCategoryDeserializer;
import com.giut.server.competition.dto.request.CompetitionSubmissionForm;
import com.giut.server.competition.dto.request.CompetitionSubmissionRequest;
import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.dto.response.CompetitionExtractionResult;
import com.giut.server.competition.dto.response.CompetitionSubmissionFieldIssue;
import com.giut.server.competition.dto.response.CompetitionSubmissionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.global.exception.ConflictException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompetitionSubmissionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CompetitionRepository competitionRepository;
    private final CompetitionUrlRepository urlRepository;
    private final CompetitionUrlNormalizer urlNormalizer;
    private final CompetitionService competitionService;
    private final Validator validator;

    /**
     * AI 원본 응답을 회원 확인용 추출 결과(초안과 보완이 필요한 필드)로 바꾼다. 비동기 추출 작업의 결과 조회가 이 변환·필드 보완 규칙을 쓴다.
     * 공식 URL 중복 같은 DB 기준 검증은 호출 시점의 데이터로 다시 계산된다.
     */
    public CompetitionExtractionResult toExtractionResult(String sourceUrl, String sourceHash,
                                                                   AiCompetitionExtractClient.Extraction extraction) {
        CompetitionSubmissionForm form = prepareForm(toForm(extraction), sourceUrl, sourceHash);
        List<CompetitionSubmissionFieldIssue> issues = validate(form);
        CompetitionExtractionResult.Status status;
        if (AiActivityType.INVALID_ACTIVITY.name().equals(extraction.activityKind())) {
            status = CompetitionExtractionResult.Status.INVALID_ACTIVITY;
        } else {
            status = issues.isEmpty()
                    ? CompetitionExtractionResult.Status.READY : CompetitionExtractionResult.Status.NEEDS_REVIEW;
        }
        return new CompetitionExtractionResult(status, null, sourceUrl, form, issues, validImages(extraction.images()));
    }

    public CompetitionSubmissionResponse submit(Long userId, CompetitionSubmissionRequest request) {
        String sourceUrl = request.sourceUrl().trim();
        CompetitionUrlNormalizer.NormalizedUrl source = urlNormalizer.normalize(sourceUrl);
        Optional<String> existing = findExistingUrl(source.hash());
        if (existing.isPresent()) {
            return CompetitionSubmissionResponse.existing(existing.get());
        }
        if (request.draft() == null) {
            return CompetitionSubmissionResponse.invalid(List.of(issue("draft", "REQUIRED", "공모전 등록 정보를 입력해 주세요.")));
        }

        CompetitionSubmissionForm form = prepareForm(request.draft(), sourceUrl, source.hash());
        List<CompetitionSubmissionFieldIssue> issues = validate(form);
        if (!issues.isEmpty()) {
            return CompetitionSubmissionResponse.invalid(issues);
        }

        try {
            Long id = competitionService.createByMember(userId, sourceUrl, source, form.toRegistrationRequest(),
                    form.isForEnrolledOnly(), form.isTeam());
            return CompetitionSubmissionResponse.submitted(id);
        } catch (DataIntegrityViolationException | ConflictException exception) {
            // The transactional write has rolled back. Read again to resolve a concurrent submission of this source.
            return findExistingUrl(source.hash()).map(CompetitionSubmissionResponse::existing)
                    .orElseThrow(() -> exception);
        }
    }

    /** 정규화 hash로 이미 등록됐거나 제출된 공고 URL을 찾는다. */
    public Optional<String> findExistingUrl(String hash) {
        return urlRepository.findFirstByNormalizedUrlHashOrderByIdAsc(hash).map(CompetitionUrl::getUrl)
                .or(() -> competitionRepository.findBySubmissionSourceUrlHash(hash).map(Competition::getSubmissionSourceUrl));
    }

    private CompetitionSubmissionForm toForm(AiCompetitionExtractClient.Extraction extraction) {
        List<CompetitionUrlRequest> links = extraction.urls() == null ? List.of() : extraction.urls().stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    CompetitionUrl.Type type = parseCode(CompetitionUrl.Type.class, item.type());
                    return type == null || validHash(item.url()) == null ? null
                            : new CompetitionUrlRequest(type, item.url().trim(), false);
                })
                .filter(Objects::nonNull)
                .toList();

        String firstCategory = extraction.category() == null || extraction.category().isEmpty()
                ? null : extraction.category().getFirst();
        Competition.Category category = null;
        if (firstCategory != null) {
            try {
                category = AiCompetitionCategoryDeserializer.resolveCategory(firstCategory);
            } catch (IllegalArgumentException exception) {
                // An unknown category becomes a required manual selection.
            }
        }

        AiActivityType kind = parseCode(AiActivityType.class, extraction.activityKind());
        Competition.ActivityType activityType = kind == null || kind == AiActivityType.INVALID_ACTIVITY
                ? null : kind.toActivityType();
        return new CompetitionSubmissionForm(extraction.title(), category, extraction.hostOrganization(),
                extraction.targetParticipants(), extraction.summary(),
                extraction.applicationStartAt() == null ? null : extraction.applicationStartAt().atStartOfDay(SEOUL).toInstant(),
                extraction.applicationEndAt() == null ? null
                        : extraction.applicationEndAt().atTime(LocalTime.of(23, 59, 59)).atZone(SEOUL).toInstant(),
                links, activityType, extraction.prizeInfo(), extraction.totalPrizeAmount(),
                extraction.isForEnrolledOnly(), extraction.isTeam());
    }

    private CompetitionSubmissionForm prepareForm(CompetitionSubmissionForm form, String sourceUrl, String sourceHash) {
        List<CompetitionUrlRequest> links = form.urls() == null ? new ArrayList<>() : new ArrayList<>(form.urls());
        int sourceIndex = -1;
        boolean hasPrimary = false;
        for (int i = 0; i < links.size(); i++) {
            CompetitionUrlRequest link = links.get(i);
            if (link == null) continue;
            if (sourceHash.equals(validHash(link.url()))) sourceIndex = i;
            if (Boolean.TRUE.equals(link.primary())) hasPrimary = true;
        }
        if (sourceIndex < 0) {
            links.add(new CompetitionUrlRequest(CompetitionUrl.Type.SOURCE_ORIGINAL, sourceUrl, !hasPrimary));
        } else if (!hasPrimary) {
            CompetitionUrlRequest link = links.get(sourceIndex);
            links.set(sourceIndex, new CompetitionUrlRequest(link.type(), link.url(), true));
        }
        return form.withUrls(links);
    }

    private List<CompetitionSubmissionFieldIssue> validate(CompetitionSubmissionForm form) {
        Map<String, CompetitionSubmissionFieldIssue> issues = new LinkedHashMap<>();
        validator.validate(form.toRegistrationRequest()).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .forEach(violation -> addConstraintIssue(issues, violation));
        if (form.activityType() == null || form.activityType() == Competition.ActivityType.NOT_ACTIVITY) {
            issues.put("activityType", issue("activityType", "INVALID_ACTIVITY_TYPE", "등록할 활동 유형을 선택해 주세요."));
        }
        if (form.applicationStartAt() != null && form.applicationEndAt() != null
                && !form.applicationEndAt().isAfter(form.applicationStartAt())) {
            issues.put("applicationEndAt", issue("applicationEndAt", "INVALID_PERIOD", "모집 마감은 시작보다 늦어야 합니다."));
        }

        Set<String> hashes = new HashSet<>();
        List<String> officialHashes = new ArrayList<>();
        long primaryCount = 0;
        for (CompetitionUrlRequest link : form.urls()) {
            String hash = link == null ? null : validHash(link.url());
            if (hash == null || link.type() == null) {
                issues.putIfAbsent("urls", issue("urls", "INVALID_URL", "공고 URL과 URL 종류를 확인해 주세요."));
                continue;
            }
            if (!hashes.add(hash)) {
                issues.putIfAbsent("urls", issue("urls", "DUPLICATE_URL", "같은 공고 URL을 한 번만 입력해 주세요."));
            }
            if (Boolean.TRUE.equals(link.primary())) primaryCount++;
            if (link.type() == CompetitionUrl.Type.OFFICIAL) officialHashes.add(hash);
        }
        if (primaryCount != 1) {
            issues.putIfAbsent("urls", issue("urls", "PRIMARY_URL_REQUIRED", "대표 URL을 정확히 하나 선택해 주세요."));
        }
        if (!issues.containsKey("urls") && !officialHashes.isEmpty()
                && !urlRepository.findAllByOfficialUrlHashIn(officialHashes).isEmpty()) {
            issues.put("urls", issue("urls", "OFFICIAL_URL_EXISTS", "이미 다른 공모전에 등록된 공식 URL입니다."));
        }
        return List.copyOf(issues.values());
    }

    private void addConstraintIssue(Map<String, CompetitionSubmissionFieldIssue> issues, ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.startsWith("urls") ? "urls" : path;
        boolean missing = violation.getInvalidValue() == null
                || violation.getInvalidValue() instanceof String text && text.isBlank();
        String reason = missing && field.equals("applicationEndAt") ? "MISSING_DEADLINE"
                : missing ? "REQUIRED" : "INVALID_VALUE";
        issues.putIfAbsent(field, issue(field, reason, violation.getMessage()));
    }

    private CompetitionSubmissionFieldIssue issue(String field, String reason, String message) {
        String label = switch (field) {
            case "draft" -> "공모전 정보";
            case "title" -> "공모전 제목";
            case "category" -> "카테고리";
            case "hostOrganization" -> "주최 기관";
            case "targetParticipants" -> "참가 대상";
            case "summary" -> "공모전 소개";
            case "applicationEndAt" -> "모집 마감 일시";
            case "activityType" -> "활동 유형";
            case "prizeInfo" -> "상금 정보";
            case "urls" -> "공고 링크";
            default -> field;
        };
        String type = switch (field) {
            case "draft" -> "object";
            case "category", "activityType" -> "select";
            case "applicationEndAt" -> "datetime";
            case "urls" -> "url-list";
            default -> "text";
        };
        List<String> options = switch (field) {
            case "category" -> Arrays.stream(Competition.Category.values()).map(Enum::name).toList();
            case "activityType" -> Arrays.stream(Competition.ActivityType.values())
                    .filter(activity -> activity != Competition.ActivityType.NOT_ACTIVITY).map(Enum::name).toList();
            default -> List.of();
        };
        return new CompetitionSubmissionFieldIssue(field, label, type, options, reason, message);
    }

    private List<String> validImages(List<String> images) {
        return images == null ? List.of() : images.stream().filter(url -> validHash(url) != null).limit(10).toList();
    }

    private String validHash(String url) {
        if (url == null || url.length() > 2000) return null;
        try {
            return urlNormalizer.normalize(url).hash();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static <T extends Enum<T>> T parseCode(Class<T> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
