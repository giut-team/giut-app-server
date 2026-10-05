package com.giut.server.team.service;

import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ConflictException;

import com.giut.server.team.dto.request.CreateTeamRequest;
import com.giut.server.team.dto.response.ApproveTeamApplicationResponse;
import com.giut.server.team.dto.response.TeamDetailResponse;
import com.giut.server.team.dto.response.TeamPageResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import com.giut.server.team.entity.TeamApplicationQuestion;
import com.giut.server.user.entity.User;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.team.repository.TeamApplicationAnswerRepository;
import com.giut.server.team.repository.TeamApplicationQuestionRepository;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRecruitmentRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.user.repository.UserRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock TeamScrapRepository teamScrapRepository;
    @Mock TeamRecruitmentRepository teamRecruitmentRepository;
    @Mock TeamMemberRepository teamMemberRepository;
    @Mock TeamApplicationRepository teamApplicationRepository;
    @Mock TeamApplicationQuestionRepository teamApplicationQuestionRepository;
    @Mock TeamApplicationAnswerRepository teamApplicationAnswerRepository;
    @Mock CompetitionRepository competitionRepository;
    @Mock UserRepository userRepository;
    @Mock ProfileRoleRepository profileRoleRepository;

    @InjectMocks TeamService teamService;

    @Test
    void rejectsAnotherRecruitingTeamForSameLeaderAndCompetition() {
        User leader = mock(User.class);
        Competition competition = mock(Competition.class);
        when(leader.getStatus()).thenReturn(User.Status.ACTIVE);
        when(competition.getId()).thenReturn(1L);
        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(leader));
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(competition));
        when(teamRepository.existsByCompetition_IdAndLeader_IdAndStatus(
                1L, 12L, Team.Status.RECRUITING)).thenReturn(true);

        assertThrows(ConflictException.class, () -> teamService.createTeam(12L, createRequest(1L)));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void createsTeamWhenNoRecruitingTeamExists() {
        User leader = mock(User.class);
        Competition competition = mock(Competition.class);
        when(leader.getStatus()).thenReturn(User.Status.ACTIVE);
        when(leader.getId()).thenReturn(12L);
        when(competition.getId()).thenReturn(1L);
        when(userRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(leader));
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(competition));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            ReflectionTestUtils.setField(team, "id", 10L);
            return team;
        });

        assertEquals(10L, teamService.createTeam(12L, createRequest(1L)).teamId());
        verify(teamRepository).existsByCompetition_IdAndLeader_IdAndStatus(
                1L, 12L, Team.Status.RECRUITING);
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    void listShowsBookmarkOnlyForCurrentUser() {
        Team first = team(12L);
        Team second = team(13L);
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(teamRepository.findAllByCompetition_IdAndStatus(
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(Team.Status.RECRUITING),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first, second)));
        when(teamScrapRepository.findScrappedTeamIds(15L, List.of(1L, 2L)))
                .thenReturn(List.of(2L));

        TeamPageResponse response = teamService.getRecruitingTeams(15L, 3L, 0, 10);

        assertFalse(response.teams().get(0).scrapped());
        assertTrue(response.teams().get(1).scrapped());
    }

    @Test
    void detailShowsCurrentUsersBookmarkStatus() {
        Team team = team(12L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findAllByTeamIdAndStatus(1L, TeamMember.Status.ACTIVE))
                .thenReturn(List.of());
        when(teamApplicationQuestionRepository.findAllByTeamIdAndStatusOrderByDisplayOrderAsc(
                1L, TeamApplicationQuestion.Status.ACTIVE)).thenReturn(List.of());
        when(teamRecruitmentRepository.findAllByTeamIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(teamScrapRepository.existsByUser_IdAndTeam_Id(15L, 1L)).thenReturn(true);

        TeamDetailResponse response = teamService.getTeamDetail(1L, 15L);

        assertTrue(response.scrapped());
    }

    @Test
    void approvalAssignsRoleAndAddsMember() {
        Team team = team(10L);
        TeamApplication application = TeamApplication.create(1L, 11L, "BACKEND", "지원합니다.");
        TeamRecruitment recruitment = TeamRecruitment.create(1L, "FRONTEND", (short) 1);
        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(team));
        when(teamApplicationRepository.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(application));
        when(teamRecruitmentRepository.findByTeamIdAndRoleCode(1L, "FRONTEND"))
                .thenReturn(Optional.of(recruitment));
        when(teamMemberRepository.save(any(TeamMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApproveTeamApplicationResponse response = teamService.approveApplication(10L, 1L, 2L, "FRONTEND");

        ArgumentCaptor<TeamMember> member = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertEquals(TeamApplication.Status.APPROVED, application.getStatus());
        assertEquals("FRONTEND", application.getAssignedRoleCode());
        assertEquals("FRONTEND", member.getValue().getRoleCode());
        assertEquals("FRONTEND", response.roleCode());
    }

    @Test
    void approvalRejectsFilledRecruitment() {
        Team team = team(10L);
        TeamApplication application = TeamApplication.create(1L, 11L, "BACKEND", null);
        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(team));
        when(teamApplicationRepository.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(application));
        when(teamRecruitmentRepository.findByTeamIdAndRoleCode(1L, "BACKEND"))
                .thenReturn(Optional.of(TeamRecruitment.create(1L, "BACKEND", (short) 1)));
        when(teamMemberRepository.countByTeamIdAndRoleCodeAndStatus(
                1L, "BACKEND", TeamMember.Status.ACTIVE)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> teamService.approveApplication(10L, 1L, 2L, "BACKEND"));
        assertEquals(TeamApplication.Status.PENDING, application.getStatus());
    }

    @Test
    void onlyApplicantCanCancelPendingApplication() {
        TeamApplication application = TeamApplication.create(1L, 11L, "BACKEND", null);
        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Team.class)));
        when(teamApplicationRepository.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(application));

        assertThrows(IllegalArgumentException.class,
                () -> teamService.cancelApplication(12L, 1L, 2L));
        assertEquals(TeamApplication.Status.PENDING, application.getStatus());

        teamService.cancelApplication(11L, 1L, 2L);
        assertEquals(TeamApplication.Status.CANCELED, application.getStatus());
    }

    @Test
    void onlyLeaderCanCloseRecruitment() {
        Team team = team(10L);
        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(team));

        assertThrows(ForbiddenException.class, () -> teamService.closeRecruitment(11L, 1L));
        assertEquals(Team.Status.RECRUITING, team.getStatus());

        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findAllByTeamIdAndStatus(1L, TeamMember.Status.ACTIVE))
                .thenReturn(List.of());
        when(teamApplicationQuestionRepository.findAllByTeamIdAndStatusOrderByDisplayOrderAsc(
                1L, TeamApplicationQuestion.Status.ACTIVE)).thenReturn(List.of());
        when(teamRecruitmentRepository.findAllByTeamIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertEquals(Team.Status.CLOSED, teamService.closeRecruitment(10L, 1L).status());
    }

    private Team team(Long leaderId) {
        User leader = mock(User.class);
        when(leader.getId()).thenReturn(leaderId);
        Competition competition = mock(Competition.class);
        return Team.create(competition, leader, "테스트 팀", null,
                Team.ActivityMode.ONLINE, (short) 4, (short) 1, Team.MeetingPlace.CAMPUS);
    }

    private CreateTeamRequest createRequest(Long competitionId) {
        return new CreateTeamRequest(competitionId, "테스트 팀", null,
                Team.ActivityMode.ONLINE, (short) 4, (short) 1,
                Team.MeetingPlace.CAMPUS, List.of(), List.of());
    }
}
