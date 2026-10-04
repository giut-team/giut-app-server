package com.giut.server.team.service;

import com.giut.server.global.exception.ForbiddenException;

import com.giut.server.team.dto.response.ApproveTeamApplicationResponse;
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
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamRepository teamRepository;
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
}
