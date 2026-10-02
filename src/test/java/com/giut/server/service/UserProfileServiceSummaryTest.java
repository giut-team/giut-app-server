package com.giut.server.service;

import com.giut.server.dto.profile.response.MyProfileResponse;
import com.giut.server.entity.TeamMember;
import com.giut.server.repository.CompetitionScrapRepository;
import com.giut.server.repository.PortfolioItemRepository;
import com.giut.server.repository.TeamMemberRepository;
import com.giut.server.repository.TeamScrapRepository;
import com.giut.server.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceSummaryTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PortfolioItemRepository portfolioItemRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private CompetitionScrapRepository competitionScrapRepository;
    @Mock private TeamScrapRepository teamScrapRepository;
    @InjectMocks private UserProfileService userProfileService;

    @Test
    void returnsCountsEvenWhenProfileIsNotCompleted() {
        when(userProfileRepository.findById(12L)).thenReturn(Optional.empty());
        when(portfolioItemRepository.countByUser_Id(12L)).thenReturn(4L);
        when(portfolioItemRepository.countByUser_IdAndShowcaseOrderIsNotNull(12L)).thenReturn(3L);
        when(teamMemberRepository.countByUserIdAndStatus(12L, TeamMember.Status.ACTIVE)).thenReturn(2L);
        when(competitionScrapRepository.countByUser_Id(12L)).thenReturn(5L);
        when(teamScrapRepository.countByUser_Id(12L)).thenReturn(3L);

        MyProfileResponse response = userProfileService.getMyProfile(12L);

        assertThat(response.profileCompleted()).isFalse();
        assertThat(response.profile()).isNull();
        assertThat(response.summary().portfolioCount()).isEqualTo(4);
        assertThat(response.summary().showcaseCount()).isEqualTo(3);
        assertThat(response.summary().myTeamCount()).isEqualTo(2);
        assertThat(response.summary().scrapCount()).isEqualTo(8);
        assertThat(response.summary().competitionScrapCount()).isEqualTo(5);
        assertThat(response.summary().teamScrapCount()).isEqualTo(3);
    }
}
