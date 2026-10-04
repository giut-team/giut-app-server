package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.PublicCompetitionDetailResponse;
import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
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
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @InjectMocks private CompetitionService competitionService;

    @Test
    void publishedCompetitionDetailIncludesTeamsAndActiveMemberCounts() {
        publishedCompetition();
        Team newestTeam = team(3L, "새 팀", 12L, Team.Status.RECRUITING);
        Team olderTeam = team(2L, "기존 팀", 15L, Team.Status.CLOSED);
        TeamMemberRepository.TeamMemberCount count = mock(TeamMemberRepository.TeamMemberCount.class);
        when(count.getTeamId()).thenReturn(3L);
        when(count.getMemberCount()).thenReturn(2L);
        when(teamRepository.findAllByCompetition_IdOrderByIdDesc(1L)).thenReturn(List.of(newestTeam, olderTeam));
        when(teamMemberRepository.countByTeamIdsAndStatus(List.of(3L, 2L), TeamMember.Status.ACTIVE))
                .thenReturn(List.of(count));

        PublicCompetitionDetailResponse response = competitionService.getPublishedCompetition(12L, 1L);

        assertThat(response.teams()).extracting("teamId").containsExactly(3L, 2L);
        assertThat(response.teams()).extracting("name").containsExactly("새 팀", "기존 팀");
        assertThat(response.teams()).extracting("myTeam").containsExactly(true, false);
        assertThat(response.teams()).extracting("description").containsExactly("팀 소개", "팀 소개");
        assertThat(response.teams()).extracting("maxMemberCount").containsExactly((short) 5, (short) 5);
        assertThat(response.teams()).extracting("currentMemberCount").containsExactly(2L, 0L);
        assertThat(response.teams()).extracting("status")
                .containsExactly(Team.Status.RECRUITING, Team.Status.CLOSED);
        assertThat(response.teamCount()).isEqualTo(2);
        assertThat(response.recruitingTeamCount()).isEqualTo(1);
    }

    @Test
    void detailReturnsEmptyTeamsWithoutCountingMembersWhenNoneExist() {
        publishedCompetition();
        when(teamRepository.findAllByCompetition_IdOrderByIdDesc(1L)).thenReturn(List.of());

        PublicCompetitionDetailResponse response = competitionService.getPublishedCompetition(12L, 1L);

        assertThat(response.teams()).isEmpty();
        assertThat(response.teamCount()).isZero();
        assertThat(response.recruitingTeamCount()).isZero();
        verify(teamMemberRepository, never()).countByTeamIdsAndStatus(any(), any());
    }

    @Test
    void competitionListIncludesTeamCount() {
        Competition competition = mock(Competition.class);
        when(competition.getId()).thenReturn(1L);
        when(competition.getCategory()).thenReturn(Competition.Category.WEB_MOBILE_IT);
        when(competitionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(competition)));
        TeamRepository.CompetitionTeamCount count = mock(TeamRepository.CompetitionTeamCount.class);
        when(count.getCompetitionId()).thenReturn(1L);
        when(count.getTeamCount()).thenReturn(5L);
        when(teamRepository.countByCompetitionIds(List.of(1L))).thenReturn(List.of(count));

        var response = competitionService.getPublishedCompetitions(
                new CompetitionSearchRequest(null, null, null, null, null)
        );

        assertThat(response.competitions()).hasSize(1);
        assertThat(response.competitions().getFirst().teamCount()).isEqualTo(5);
    }

    @Test
    void unpublishedCompetitionDoesNotExposeTeams() {
        assertThatThrownBy(() -> competitionService.getPublishedCompetition(12L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(teamRepository, never()).findAllByCompetition_IdOrderByIdDesc(any());
    }

    private Competition publishedCompetition() {
        Competition competition = mock(Competition.class);
        when(competitionRepository.findByIdAndPublicationStatus(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(competition));
        when(competition.getCategory()).thenReturn(Competition.Category.WEB_MOBILE_IT);
        return competition;
    }

    private Team team(Long id, String name, Long leaderId, Team.Status status) {
        Team team = mock(Team.class);
        User leader = mock(User.class);
        when(team.getId()).thenReturn(id);
        when(team.getName()).thenReturn(name);
        when(team.getLeader()).thenReturn(leader);
        when(leader.getId()).thenReturn(leaderId);
        when(team.getDescription()).thenReturn("팀 소개");
        when(team.getMaxMemberCount()).thenReturn((short) 5);
        when(team.getStatus()).thenReturn(status);
        return team;
    }
}
