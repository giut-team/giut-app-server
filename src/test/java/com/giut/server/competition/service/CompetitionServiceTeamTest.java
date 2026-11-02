package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.PublicCompetitionDetailResponse;
import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.competition.repository.CompetitionScrapSummaryProjection;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionTeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTeamTest {

    @Mock private CompetitionRepository competitionRepository;
    @Mock private CompetitionScrapRepository competitionScrapRepository;
    @Mock private CompetitionUrlRepository competitionUrlRepository;
    @Mock private CompetitionTeamRepository competitionTeamRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private CompetitionService competitionService;

    @Test
    void scrapAndRemovalAcquireUserLockBeforeCheckingOrChangingScrap() {
        User user = User.createOAuthUser("member@example.com", "회원", User.OAuthProvider.KAKAO, "kakao-12");
        Competition competition = mock(Competition.class);
        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(user));
        when(competitionRepository.findByIdAndPublicationStatus(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(competition));
        assertThat(competitionService.scrapCompetition(12L, 1L).scrapped()).isTrue();
        assertThat(competitionService.removeCompetitionScrap(12L, 1L).scrapped()).isFalse();
        var order = org.mockito.Mockito.inOrder(userRepository, competitionScrapRepository);
        order.verify(userRepository).findByIdForUpdate(12L);
        order.verify(competitionScrapRepository).existsByUser_IdAndCompetition_Id(12L, 1L);
        order.verify(competitionScrapRepository).save(any());
        order.verify(userRepository).findByIdForUpdate(12L);
        order.verify(competitionScrapRepository).deleteByUser_IdAndCompetition_Id(12L, 1L);
    }

    @Test
    void publishedCompetitionDetailIncludesTeamsAndActiveMemberCounts() {
        publishedCompetition();
        var newestTeamRow = teamWithCount(3L, "새 팀", 12L, "팀 소개", (short) 5, Team.Status.RECRUITING, 2L);
        var olderTeamRow = teamWithCount(2L, "기존 팀", 15L, "팀 소개", (short) 5, Team.Status.RECRUITING, 0L);
        when(competitionTeamRepository.findWithActiveMemberCount(1L, Team.Status.RECRUITING, TeamMember.Status.ACTIVE))
                .thenReturn(List.of(newestTeamRow, olderTeamRow));
        // 모집 중 2팀 + 마감/보관된 팀도 있다고 가정해 teamCount와 recruitingTeamCount가 실제로 달라지는지 확인한다.
        when(competitionTeamRepository.countByCompetition_Id(1L)).thenReturn(5L);

        PublicCompetitionDetailResponse response = competitionService.getPublishedCompetition(12L, 1L);

        assertThat(response.teams()).extracting("teamId").containsExactly(3L, 2L);
        assertThat(response.teams()).extracting("name").containsExactly("새 팀", "기존 팀");
        assertThat(response.teams()).extracting("myTeam").containsExactly(true, false);
        assertThat(response.teams()).extracting("description").containsExactly("팀 소개", "팀 소개");
        assertThat(response.teams()).extracting("maxMemberCount").containsExactly((short) 5, (short) 5);
        assertThat(response.teams()).extracting("currentMemberCount").containsExactly(2L, 0L);
        assertThat(response.teams()).extracting("status")
                .containsExactly(Team.Status.RECRUITING, Team.Status.RECRUITING);
        assertThat(response.teamCount()).isEqualTo(5);
        assertThat(response.recruitingTeamCount()).isEqualTo(2);
        verify(competitionTeamRepository)
                .findWithActiveMemberCount(1L, Team.Status.RECRUITING, TeamMember.Status.ACTIVE);
    }

    @Test
    void detailReturnsEmptyTeamsWhenNoneExist() {
        publishedCompetition();
        when(competitionTeamRepository.findWithActiveMemberCount(1L, Team.Status.RECRUITING, TeamMember.Status.ACTIVE))
                .thenReturn(List.of());
        // 모집 중인 팀은 없지만, 이미 마감된 팀은 있는 경우 teamCount만 그걸 반영해야 한다.
        when(competitionTeamRepository.countByCompetition_Id(1L)).thenReturn(4L);

        PublicCompetitionDetailResponse response = competitionService.getPublishedCompetition(12L, 1L);

        assertThat(response.teams()).isEmpty();
        assertThat(response.teamCount()).isEqualTo(4);
        assertThat(response.recruitingTeamCount()).isZero();
    }

    @Test
    void competitionListIncludesTeamCount() {
        Competition competition = mock(Competition.class);
        when(competition.getId()).thenReturn(1L);
        when(competition.getCategory()).thenReturn(Competition.Category.WEB_MOBILE_IT);
        when(competitionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(competition)));
        CompetitionTeamRepository.CompetitionTeamCount count = mock(CompetitionTeamRepository.CompetitionTeamCount.class);
        when(count.getCompetitionId()).thenReturn(1L);
        when(count.getTeamCount()).thenReturn(5L);
        when(competitionTeamRepository.countByCompetitionIdsAndStatus(List.of(1L), Team.Status.RECRUITING))
                .thenReturn(List.of(count));

        var response = competitionService.getCompetitions(
                new CompetitionSearchRequest(null, null, null, null, null)
        );

        assertThat(response.competitions()).hasSize(1);
        assertThat(response.competitions().getFirst().teamCount()).isEqualTo(5);
    }

    @Test
    void unpublishedCompetitionDoesNotExposeTeams() {
        assertThatThrownBy(() -> competitionService.getPublishedCompetition(12L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(competitionTeamRepository, never()).findWithActiveMemberCount(any(), any(), any());
        verify(competitionTeamRepository, never()).countByCompetition_Id(any());
    }

    private Competition publishedCompetition() {
        Competition competition = mock(Competition.class);
        when(competitionRepository.findByIdAndPublicationStatus(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(competition));
        when(competitionRepository.incrementViewCount(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(1);
        when(competition.getCategory()).thenReturn(Competition.Category.WEB_MOBILE_IT);
        CompetitionScrapSummaryProjection scrapSummary = scrapSummary(0L, false);
        when(competitionScrapRepository.findScrapSummary(12L, 1L)).thenReturn(scrapSummary);
        return competition;
    }

    private CompetitionScrapSummaryProjection scrapSummary(long scrapCount, boolean scrapped) {
        var summary = mock(CompetitionScrapSummaryProjection.class);
        when(summary.getScrapCount()).thenReturn(scrapCount);
        when(summary.isScrapped()).thenReturn(scrapped);
        return summary;
    }

    private CompetitionTeamRepository.RecruitingTeamWithActiveMemberCount teamWithCount(
            Long teamId, String name, Long leaderId, String description,
            Short maxMemberCount, Team.Status status, long activeMemberCount) {
        var row = mock(CompetitionTeamRepository.RecruitingTeamWithActiveMemberCount.class);
        when(row.getTeamId()).thenReturn(teamId);
        when(row.getName()).thenReturn(name);
        when(row.getLeaderId()).thenReturn(leaderId);
        when(row.getDescription()).thenReturn(description);
        when(row.getMaxMemberCount()).thenReturn(maxMemberCount);
        when(row.getStatus()).thenReturn(status);
        when(row.getActiveMemberCount()).thenReturn(activeMemberCount);
        return row;
    }
}
