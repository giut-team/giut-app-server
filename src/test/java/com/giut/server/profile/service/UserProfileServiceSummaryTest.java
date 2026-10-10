package com.giut.server.profile.service;

import com.giut.server.profile.dto.response.MyProfileResponse;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ActivityHistoryRepository;
import com.giut.server.profile.repository.ProfileRoleSkillTagRepository;
import com.giut.server.profile.repository.ProfileTagRepository;
import com.giut.server.profile.repository.UserProfileRoleRepository;
import com.giut.server.profile.repository.UserProfileTagRepository;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.ProfileCollaborationRepository;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceSummaryTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRoleRepository userProfileRoleRepository;
    @Mock private UserProfileTagRepository userProfileTagRepository;
    @Mock private ProfileTagRepository profileTagRepository;
    @Mock private ProfileRoleSkillTagRepository profileRoleSkillTagRepository;
    @Mock private ActivityHistoryRepository activityHistoryRepository;
    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamApplicationRepository teamApplicationRepository;
    @Mock private CompetitionScrapRepository competitionScrapRepository;
    @Mock private TeamScrapRepository teamScrapRepository;
    @Mock private ProfileRecommendationRepository profileRecommendationRepository;
    @Mock private ProfileCollaborationRepository profileCollaborationRepository;
    @InjectMocks private UserProfileService userProfileService;

    @Test
    void returnsCountsEvenWhenProfileIsNotCompleted() {
        when(userProfileRepository.findById(12L)).thenReturn(Optional.empty());
        when(portfolioItemRepository.countByUser_Id(12L)).thenReturn(4L);
        when(portfolioItemRepository.countByUser_IdAndShowcaseOrderIsNotNull(12L)).thenReturn(3L);
        when(teamMemberRepository.countByUserIdAndStatus(12L, TeamMember.Status.ACTIVE)).thenReturn(2L);
        when(competitionScrapRepository.countByUser_Id(12L)).thenReturn(5L);
        when(teamScrapRepository.countByUser_Id(12L)).thenReturn(3L);
        when(profileRecommendationRepository.countByRecommendedUser_Id(12L)).thenReturn(7L);
        when(profileCollaborationRepository.countParticipatedTeams(12L)).thenReturn(4L);
        when(teamApplicationRepository.countByUserIdAndTypeAndStatus(
                12L, TeamApplication.Type.INVITATION, TeamApplication.Status.PENDING
        )).thenReturn(2L);

        MyProfileResponse response = userProfileService.getMyProfile(12L);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.universityVerified()).isFalse();
        assertThat(response.profile()).isNull();
        assertThat(response.summary().portfolioCount()).isEqualTo(4);
        assertThat(response.summary().showcaseCount()).isEqualTo(3);
        assertThat(response.summary().myTeamCount()).isEqualTo(2);
        assertThat(response.summary().scrapCount()).isEqualTo(8);
        assertThat(response.summary().competitionScrapCount()).isEqualTo(5);
        assertThat(response.summary().teamScrapCount()).isEqualTo(3);
        assertThat(response.summary().receivedProposalCount()).isEqualTo(2);
        assertThat(response.summary().receivedRecommendationCount()).isEqualTo(7);
        assertThat(response.summary().collaborationCount()).isEqualTo(4);
        verify(profileRecommendationRepository).countByRecommendedUser_Id(12L);
        verify(profileCollaborationRepository).countParticipatedTeams(12L);
        verify(teamApplicationRepository).countByUserIdAndTypeAndStatus(
                12L, TeamApplication.Type.INVITATION, TeamApplication.Status.PENDING
        );
    }

    @Test
    void newStatisticsDefaultToZeroWhenNoHistoryExists() {
        when(userProfileRepository.findById(12L)).thenReturn(Optional.empty());
        var response = userProfileService.getMyProfile(12L);
        assertThat(response.summary().receivedProposalCount()).isZero();
        assertThat(response.summary().receivedRecommendationCount()).isZero();
        assertThat(response.summary().collaborationCount()).isZero();
    }

    @Test
    void publicProfileDetailIncludesPendingReceivedProposalCount() {
        UserProfile profile = org.mockito.Mockito.mock(UserProfile.class);
        User user = org.mockito.Mockito.mock(User.class);
        when(userProfileRepository.findById(13L)).thenReturn(Optional.of(profile));
        when(profile.isSearchable()).thenReturn(true);
        when(profile.getActivityStatus()).thenReturn(UserProfile.ActivityStatus.LOOKING_FOR_TEAM);
        when(profile.getUserId()).thenReturn(13L);
        when(profile.getPrimaryRolesJson()).thenReturn("[]");
        when(profile.getDepartment()).thenReturn(UserProfile.DepartmentType.COMPUTER_SCIENCE);
        when(profile.getGrade()).thenReturn((short) 3);
        when(profile.getUser()).thenReturn(user);
        when(userRepository.findById(13L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(User.Status.ACTIVE);
        when(user.getNickname()).thenReturn("테스트 사용자");
        when(user.getUniversityVerifiedAt()).thenReturn(null);
        when(userProfileRoleRepository.findAllByProfile_UserId(13L)).thenReturn(java.util.List.of());
        when(userProfileTagRepository.findAllByProfile_UserId(13L)).thenReturn(java.util.List.of());
        when(profileTagRepository.findAllById(java.util.List.of())).thenReturn(java.util.List.of());
        when(profileRoleSkillTagRepository.findAllByTag_IdIn(java.util.List.of())).thenReturn(java.util.List.of());
        when(portfolioItemRepository.findAllByUser_IdAndShowcaseOrderIsNotNullOrderByShowcaseOrderAsc(13L))
                .thenReturn(java.util.List.of());
        when(activityHistoryRepository.findAllByUser_IdOrderByStartMonthDescEndMonthDescIdDesc(13L))
                .thenReturn(java.util.List.of());
        when(teamApplicationRepository.countByUserIdAndTypeAndStatus(
                13L, TeamApplication.Type.INVITATION, TeamApplication.Status.PENDING
        )).thenReturn(3L);

        var response = userProfileService.getPublicProfile(13L);

        assertThat(response.receivedProposalCount()).isEqualTo(3);
    }

    @Test
    void reportsUniversityVerificationWhenProfileIsNotCompleted() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userProfileRepository.findById(12L)).thenReturn(Optional.empty());
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(user.getUniversityVerifiedAt()).thenReturn(LocalDateTime.parse("2026-10-09T12:00:00"));

        MyProfileResponse response = userProfileService.getMyProfile(12L);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.universityVerified()).isTrue();
        assertThat(response.profile()).isNull();
    }
}
