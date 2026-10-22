package com.giut.server.profile.service;

import com.giut.server.profile.dto.request.CreateProfileReportRequest;
import com.giut.server.profile.dto.request.ReviewProfileReportRequest;
import com.giut.server.profile.dto.response.ProfileReportResponse;
import com.giut.server.profile.entity.ProfileReport;
import com.giut.server.user.entity.User;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.profile.repository.ProfileReportRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileReportServiceTest {

    @Mock private ProfileReportRepository profileReportRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @InjectMocks private ProfileReportService profileReportService;

    @Test
    void reportReasonsMatchTheFourOptionsOnTheProfileScreen() {
        assertThat(ProfileReport.Reason.values()).containsExactly(
                ProfileReport.Reason.SPAM_ADVERTISING,
                ProfileReport.Reason.FALSE_INFORMATION_IMPERSONATION,
                ProfileReport.Reason.INAPPROPRIATE_BEHAVIOR,
                ProfileReport.Reason.OTHER
        );
    }

    @Test
    void reportPublicProfileAcceptsNullDescriptionAndStoresSnapshot() {
        User reporter = user(1L);
        UserProfile target = profile(2L, true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reporter));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(target));
        when(profileReportRepository.save(any(ProfileReport.class))).thenAnswer(invocation -> {
            ProfileReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 10L);
            return report;
        });

        ProfileReportResponse response = profileReportService.reportPublicProfile(1L, 2L,
                new CreateProfileReportRequest(ProfileReport.Reason.OTHER, null));

        ArgumentCaptor<ProfileReport> saved = ArgumentCaptor.forClass(ProfileReport.class);
        verify(profileReportRepository).save(saved.capture());
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ProfileReport.Status.PENDING);
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getSnapshotNickname()).isEqualTo("회원2");
        assertThat(saved.getValue().getSnapshotBio()).isEqualTo("소개");
    }

    @Test
    void duplicatePendingReportIsRejected() {
        User reporter = user(1L);
        UserProfile target = profile(2L, true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reporter));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(target));
        when(profileReportRepository.existsByReporter_IdAndReportedProfile_UserIdAndStatus(
                1L, 2L, ProfileReport.Status.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> profileReportService.reportPublicProfile(1L, 2L,
                new CreateProfileReportRequest(ProfileReport.Reason.SPAM_ADVERTISING, null)))
                .isInstanceOf(ConflictException.class);
        verify(profileReportRepository, never()).save(any());
    }

    @Test
    void reportingOwnProfileIsRejected() {
        User reporter = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reporter));
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(profile(1L, true)));

        assertThatThrownBy(() -> profileReportService.reportPublicProfile(1L, 1L,
                new CreateProfileReportRequest(ProfileReport.Reason.OTHER, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(profileReportRepository, never()).save(any());
    }

    @Test
    void hiddenProfileCannotBeReportedThroughPublicEndpoint() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(profile(2L, false)));

        assertThatThrownBy(() -> profileReportService.reportPublicProfile(1L, 2L,
                new CreateProfileReportRequest(ProfileReport.Reason.OTHER, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(profileReportRepository, never()).save(any());
    }

    @Test
    void adminCanReviewPendingReportOnlyOnce() {
        User admin = User.createAdmin("admin@example.com", "hash", "관리자");
        ReflectionTestUtils.setField(admin, "id", 3L);
        ProfileReport report = ProfileReport.create(user(1L), profile(2L, true),
                ProfileReport.Reason.OTHER, null);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(profileReportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));

        profileReportService.reviewReport(3L, 10L,
                new ReviewProfileReportRequest(ProfileReport.Status.DISMISSED, "사실 확인 불가"));

        assertThat(report.getStatus()).isEqualTo(ProfileReport.Status.DISMISSED);
        assertThat(report.getReviewedBy()).isSameAs(admin);
        assertThat(report.getReviewedAt()).isNotNull();
        assertThatThrownBy(() -> profileReportService.reviewReport(3L, 10L,
                new ReviewProfileReportRequest(ProfileReport.Status.ACTIONED, "재처리")))
                .isInstanceOf(ConflictException.class);
    }

    private User user(Long id) {
        User user = User.createOAuthUser("member" + id + "@example.com", "회원" + id,
                User.OAuthProvider.KAKAO, "kakao-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile profile(Long userId, boolean searchable) {
        UserProfile profile = UserProfile.create(user(userId), UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null, "소개",
                searchable, "[\"DEVELOPMENT\"]");
        ReflectionTestUtils.setField(profile, "userId", userId);
        return profile;
    }
}
