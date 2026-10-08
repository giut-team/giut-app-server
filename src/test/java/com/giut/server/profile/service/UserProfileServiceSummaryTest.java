package com.giut.server.profile.service;

import com.giut.server.profile.dto.response.MyProfileResponse;
import com.giut.server.profile.repository.ProfileRecommendationRepository;
import com.giut.server.profile.repository.ProfileCollaborationRepository;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.profile.repository.PortfolioItemRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceSummaryTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
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

        MyProfileResponse response = userProfileService.getMyProfile(12L);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.profile()).isNull();
        assertThat(response.summary().portfolioCount()).isEqualTo(4);
        assertThat(response.summary().showcaseCount()).isEqualTo(3);
        assertThat(response.summary().myTeamCount()).isEqualTo(2);
        assertThat(response.summary().scrapCount()).isEqualTo(8);
        assertThat(response.summary().competitionScrapCount()).isEqualTo(5);
        assertThat(response.summary().teamScrapCount()).isEqualTo(3);
        assertThat(response.summary().receivedRecommendationCount()).isEqualTo(7);
        assertThat(response.summary().collaborationCount()).isEqualTo(4);
        verify(profileRecommendationRepository).countByRecommendedUser_Id(12L);
        verify(profileCollaborationRepository).countParticipatedTeams(12L);
    }

    @Test
    void newStatisticsDefaultToZeroWhenNoHistoryExists() {
        when(userProfileRepository.findById(12L)).thenReturn(Optional.empty());
        var response = userProfileService.getMyProfile(12L);
        assertThat(response.summary().receivedRecommendationCount()).isZero();
        assertThat(response.summary().collaborationCount()).isZero();
    }
}
