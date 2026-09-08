package com.giut.server.service;

import com.giut.server.dto.team.request.CreateTeamRequest;
import com.giut.server.dto.team.request.ApplyTeamRequest;
import com.giut.server.dto.team.response.ApproveTeamApplicationResponse;
import com.giut.server.dto.team.response.CreateTeamResponse;
import com.giut.server.dto.team.response.TeamApplicationResponse;
import com.giut.server.entity.ChatRoom;
import com.giut.server.entity.ChatRoomMember;
import com.giut.server.entity.Competition;
import com.giut.server.entity.Team;
import com.giut.server.entity.TeamApplication;
import com.giut.server.entity.TeamMember;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.ChatRoomMemberRepository;
import com.giut.server.repository.ChatRoomRepository;
import com.giut.server.repository.CompetitionRepository;
import com.giut.server.repository.TeamApplicationRepository;
import com.giut.server.repository.TeamMemberRepository;
import com.giut.server.repository.TeamRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamApplicationRepository teamApplicationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final CompetitionRepository competitionRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateTeamResponse createTeam(Long leaderUserId, CreateTeamRequest request) {
        User leader = userRepository.findById(leaderUserId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("팀장을 찾을 수 없습니다."));

        Competition competition = competitionRepository.findById(request.competitionId())
                .orElseThrow(() -> new ResourceNotFoundException("대회를 찾을 수 없습니다."));

        Team team = teamRepository.save(Team.create(
                competition,
                leader.getId(),
                request.name(),
                request.description(),
                request.activityMode(),
                request.maxMemberCount()
        ));

        teamMemberRepository.save(TeamMember.createLeader(team.getId(), leader.getId()));

        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.createTeamRoom(team.getId()));
        chatRoomMemberRepository.save(ChatRoomMember.join(chatRoom.getId(), leader.getId()));

        return CreateTeamResponse.of(team, chatRoom);
    }

    @Transactional
    public TeamApplicationResponse applyTeam(Long userId, Long teamId, ApplyTeamRequest request) {
        User user = findActiveUser(userId, "신청자를 찾을 수 없습니다.");
        Team team = findTeam(teamId);

        if (team.getStatus() != Team.Status.RECRUITING) {
            throw new IllegalArgumentException("모집 중인 팀에만 참가 신청할 수 있습니다.");
        }

        if (team.getLeaderUserId().equals(user.getId())) {
            throw new IllegalArgumentException("팀장은 자신의 팀에 참가 신청할 수 없습니다.");
        }

        if (teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, user.getId(), TeamMember.Status.ACTIVE)) {
            throw new IllegalArgumentException("이미 참여 중인 팀입니다.");
        }

        if (teamApplicationRepository.existsByTeamIdAndUserIdAndStatus(teamId, user.getId(), TeamApplication.Status.PENDING)) {
            throw new IllegalArgumentException("이미 승인 대기 중인 참가 신청이 있습니다.");
        }

        TeamApplication application = teamApplicationRepository.save(
                TeamApplication.create(team.getId(), user.getId(), request.message())
        );

        return TeamApplicationResponse.from(application);
    }

    @Transactional
    public ApproveTeamApplicationResponse approveApplication(Long leaderUserId, Long teamId, Long applicationId) {
        Team team = findTeam(teamId);
        validateTeamLeader(team, leaderUserId);

        TeamApplication application = findPendingApplication(teamId, applicationId);

        if (teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, application.getUserId(), TeamMember.Status.ACTIVE)) {
            throw new IllegalArgumentException("이미 참여 중인 사용자입니다.");
        }

        long activeMemberCount = teamMemberRepository.countByTeamIdAndStatus(teamId, TeamMember.Status.ACTIVE);
        if (team.getMaxMemberCount() != null && activeMemberCount >= team.getMaxMemberCount()) {
            throw new IllegalArgumentException("팀 정원이 이미 마감되었습니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findByTeamIdAndType(teamId, ChatRoom.Type.TEAM)
                .orElseThrow(() -> new ResourceNotFoundException("팀 채팅방을 찾을 수 없습니다."));

        application.approve();
        TeamMember teamMember = teamMemberRepository.save(TeamMember.createMember(teamId, application.getUserId()));
        ChatRoomMember chatRoomMember = chatRoomMemberRepository.save(
                ChatRoomMember.join(chatRoom.getId(), application.getUserId())
        );

        return new ApproveTeamApplicationResponse(
                application.getId(),
                application.getTeamId(),
                application.getUserId(),
                application.getStatus(),
                teamMember.getId(),
                chatRoom.getId(),
                chatRoomMember.getId()
        );
    }

    @Transactional
    public TeamApplicationResponse rejectApplication(Long leaderUserId, Long teamId, Long applicationId) {
        Team team = findTeam(teamId);
        validateTeamLeader(team, leaderUserId);

        TeamApplication application = findPendingApplication(teamId, applicationId);
        application.reject();

        return TeamApplicationResponse.from(application);
    }

    private User findActiveUser(Long userId, String notFoundMessage) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("팀을 찾을 수 없습니다."));
    }

    private TeamApplication findPendingApplication(Long teamId, Long applicationId) {
        return teamApplicationRepository.findByIdAndTeamId(applicationId, teamId)
                .filter(application -> application.getStatus() == TeamApplication.Status.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("승인 대기 중인 참가 신청을 찾을 수 없습니다."));
    }

    private void validateTeamLeader(Team team, Long userId) {
        if (!team.getLeaderUserId().equals(userId)) {
            throw new IllegalArgumentException("팀장만 참가 신청을 처리할 수 있습니다.");
        }
    }
}
