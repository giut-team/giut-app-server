package com.giut.server.service;

import com.giut.server.dto.profile.request.CreateProfileReportRequest;
import com.giut.server.dto.profile.request.ReviewProfileReportRequest;
import com.giut.server.dto.profile.response.AdminProfileReportDetailResponse;
import com.giut.server.dto.profile.response.ProfileReportPageResponse;
import com.giut.server.dto.profile.response.ProfileReportResponse;
import com.giut.server.entity.ProfileReport;
import com.giut.server.entity.User;
import com.giut.server.entity.UserProfile;
import com.giut.server.exception.ConflictException;
import com.giut.server.exception.ForbiddenException;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ProfileReportRepository;
import com.giut.server.repository.UserProfileRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileReportService {

    private final ProfileReportRepository profileReportRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public ProfileReportResponse reportPublicProfile(Long reporterUserId, Long targetUserId,
                                                     CreateProfileReportRequest request) {
        User reporter = findActiveReporterForUpdate(reporterUserId);
        UserProfile profile = findActiveProfile(targetUserId);
        if (!profile.isSearchable() || profile.getActivityStatus() == UserProfile.ActivityStatus.RESTING) {
            throw new ResourceNotFoundException("공개 프로필을 찾을 수 없습니다.");
        }
        return createReport(reporter, profile, request);
    }

    @Transactional(readOnly = true)
    public ProfileReportPageResponse getReports(Long adminUserId, ProfileReport.Status status, int page, int size) {
        findActiveAdmin(adminUserId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ProfileReport> reports = status == null
                ? profileReportRepository.findAll(pageable)
                : profileReportRepository.findAllByStatus(status, pageable);
        return ProfileReportPageResponse.from(reports);
    }

    @Transactional(readOnly = true)
    public AdminProfileReportDetailResponse getReport(Long adminUserId, Long reportId) {
        findActiveAdmin(adminUserId);
        return AdminProfileReportDetailResponse.from(findReport(reportId));
    }

    @Transactional
    public AdminProfileReportDetailResponse reviewReport(Long adminUserId, Long reportId,
                                                         ReviewProfileReportRequest request) {
        User admin = findActiveAdmin(adminUserId);
        if (request.status() == ProfileReport.Status.PENDING) {
            throw new IllegalArgumentException("검토 결과는 ACTIONED 또는 DISMISSED여야 합니다.");
        }
        ProfileReport report = profileReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("신고를 찾을 수 없습니다."));
        if (report.getStatus() != ProfileReport.Status.PENDING) {
            throw new ConflictException("이미 처리된 신고입니다.");
        }
        report.review(admin, request.status(), request.reviewNote().trim());
        return AdminProfileReportDetailResponse.from(report);
    }

    private ProfileReportResponse createReport(User reporter, UserProfile profile,
                                               CreateProfileReportRequest request) {
        Long targetUserId = profile.getUserId();
        if (reporter.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("본인 프로필은 신고할 수 없습니다.");
        }
        if (profileReportRepository.existsByReporter_IdAndReportedProfile_UserIdAndStatus(
                reporter.getId(), targetUserId, ProfileReport.Status.PENDING)) {
            throw new ConflictException("이미 검토 중인 신고가 있습니다.");
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        ProfileReport report = profileReportRepository.save(
                ProfileReport.create(reporter, profile, request.reason(), description));
        return ProfileReportResponse.from(report);
    }

    private User findActiveReporterForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("신고할 수 없는 사용자입니다."));
    }

    private UserProfile findActiveProfile(Long targetUserId) {
        return userProfileRepository.findById(targetUserId)
                .filter(profile -> profile.getUser().getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("신고할 프로필을 찾을 수 없습니다."));
    }

    private User findActiveAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == User.Role.ADMIN && user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("관리자 권한이 필요합니다."));
    }

    private ProfileReport findReport(Long reportId) {
        return profileReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("신고를 찾을 수 없습니다."));
    }
}
