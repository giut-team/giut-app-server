package com.giut.server.team.service;

import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.team.dto.request.CreateTeamRequest;
import com.giut.server.team.dto.request.ApplyTeamRequest;
import com.giut.server.team.dto.request.CreateTeamQuestionRequest;
import com.giut.server.team.dto.request.TeamApplicationAnswerRequest;
import com.giut.server.team.dto.request.CreateTeamRecruitmentRequest;
import com.giut.server.team.dto.response.ApproveTeamApplicationResponse;
import com.giut.server.team.dto.response.CreateTeamResponse;
import com.giut.server.team.dto.response.TeamApplicationAnswerResponse;
import com.giut.server.team.dto.response.TeamApplicationListResponse;
import com.giut.server.team.dto.response.TeamApplicationQuestionResponse;
import com.giut.server.team.dto.response.TeamApplicationResponse;
import com.giut.server.team.dto.response.TeamDetailResponse;
import com.giut.server.team.dto.response.TeamMemberListResponse;
import com.giut.server.team.dto.response.TeamMemberResponse;
import com.giut.server.team.dto.response.TeamRecruitmentResponse;
import com.giut.server.team.dto.response.TeamRecruitmentListResponse;
import com.giut.server.team.dto.response.MyTeamApplicationListResponse;
import com.giut.server.team.dto.response.TeamPageResponse;
import com.giut.server.team.dto.response.TeamSummaryResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamApplicationAnswer;
import com.giut.server.team.entity.TeamApplicationQuestion;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import com.giut.server.user.entity.User;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.team.repository.TeamApplicationAnswerRepository;
import com.giut.server.team.repository.TeamApplicationQuestionRepository;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.team.repository.TeamRecruitmentRepository;
import com.giut.server.profile.repository.ProfileRoleRepository;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamRecruitmentRepository teamRecruitmentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamApplicationRepository teamApplicationRepository;
    private final TeamApplicationQuestionRepository teamApplicationQuestionRepository;
    private final TeamApplicationAnswerRepository teamApplicationAnswerRepository;
    private final CompetitionRepository competitionRepository;
    private final UserRepository userRepository;
    private final ProfileRoleRepository profileRoleRepository;

    @Transactional(readOnly = true)
    public TeamPageResponse getRecruitingTeams(Long competitionId, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 10 : Math.min(size, 20);
        Page<Team> teams = teamRepository.findAllByCompetition_IdAndStatus(
                competitionId,
                Team.Status.RECRUITING,
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "id"))
        );
        List<TeamSummaryResponse> summaries = teams.getContent().stream()
                .map(team -> TeamSummaryResponse.of(
                        team,
                        teamMemberRepository.countByTeamIdAndStatus(team.getId(), TeamMember.Status.ACTIVE)
                ))
                .toList();
        return new TeamPageResponse(summaries, normalizedPage, normalizedSize,
                teams.getTotalElements(), teams.getTotalPages(), teams.hasNext());
    }

    @Transactional
    public CreateTeamResponse createTeam(Long leaderUserId, CreateTeamRequest request) {
        User leader = userRepository.findById(leaderUserId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("팀장을 찾을 수 없습니다."));

        Competition competition = competitionRepository.findById(request.competitionId())
                .orElseThrow(() -> new ResourceNotFoundException("대회를 찾을 수 없습니다."));

        Team team = teamRepository.save(Team.create(
                competition,
                leader,
                request.name(),
                request.description(),
                request.activityMode(),
                request.maxMemberCount(),
                request.weeklyMeetingCount(),
                request.meetingPlace()
        ));

        teamMemberRepository.save(TeamMember.createLeader(team.getId(), leader.getId()));

        List<TeamRecruitmentResponse> recruitments = saveRecruitments(
                team.getId(),
                request.recruitments()
        );

        List<TeamApplicationQuestionResponse> applicationQuestions = saveApplicationQuestions(
                team.getId(),
                request.applicationQuestions()
        );

        return CreateTeamResponse.of(team, recruitments, applicationQuestions);
    }

    @Transactional(readOnly = true)
    public TeamDetailResponse getTeamDetail(Long teamId) {
        Team team = findTeam(teamId);
        List<TeamMember> activeMembers = teamMemberRepository.findAllByTeamIdAndStatus(
                teamId,
                TeamMember.Status.ACTIVE
        );
        List<TeamApplicationQuestionResponse> applicationQuestions = findActiveQuestions(teamId).stream()
                .map(TeamApplicationQuestionResponse::from)
                .toList();
        List<TeamRecruitmentResponse> recruitments = teamRecruitmentRepository
                .findAllByTeamIdOrderByIdAsc(teamId)
                .stream()
                .map(recruitment -> toRecruitmentResponse(recruitment))
                .toList();

        return new TeamDetailResponse(
                team.getId(),
                team.getCompetition().getId(),
                team.getLeader().getId(),
                team.getName(),
                team.getDescription(),
                team.getActivityMode(),
                team.getMaxMemberCount(),
                activeMembers.size(),
                team.getWeeklyMeetingCount(),
                team.getMeetingPlace(),
                recruitments,
                team.getStatus(),
                team.getCreatedAt(),
                applicationQuestions
        );
    }

    @Transactional(readOnly = true)
    public TeamMemberListResponse getTeamMembers(Long teamId) {
        findTeam(teamId);

        List<TeamMember> activeMembers = teamMemberRepository.findAllByTeamIdAndStatus(
                teamId,
                TeamMember.Status.ACTIVE
        );
        List<Long> userIds = activeMembers.stream()
                .map(TeamMember::getUserId)
                .toList();
        Map<Long, User> usersById = userRepository.findAllByIdInAndStatus(userIds, User.Status.ACTIVE)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<TeamMemberResponse> members = activeMembers.stream()
                .map(teamMember -> {
                    User user = usersById.get(teamMember.getUserId());
                    if (user == null) {
                        return null;
                    }
                    return new TeamMemberResponse(
                            teamMember.getId(),
                            user.getId(),
                            user.getNickname(),
                            teamMember.getRole(),
                            teamMember.getRoleCode(),
                            teamMember.getStatus(),
                            teamMember.getJoinedAt()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return new TeamMemberListResponse(teamId, members);
    }

    @Transactional(readOnly = true)
    public TeamRecruitmentListResponse getTeamRecruitments(Long teamId) {
        findTeam(teamId);

        List<TeamRecruitmentResponse> recruitments = teamRecruitmentRepository
                .findAllByTeamIdOrderByIdAsc(teamId)
                .stream()
                .map(recruitment -> toRecruitmentResponse(recruitment))
                .toList();

        return new TeamRecruitmentListResponse(teamId, recruitments);
    }

    @Transactional
    public TeamApplicationResponse applyTeam(Long userId, Long teamId, ApplyTeamRequest request) {
        User user = findActiveUser(userId, "신청자를 찾을 수 없습니다.");
        Team team = findTeamForUpdate(teamId);

        if (team.getStatus() != Team.Status.RECRUITING) {
            throw new ConflictException("모집 중인 팀에만 참가 신청할 수 있습니다.");
        }

        if (team.getLeader().getId().equals(user.getId())) {
            throw new IllegalArgumentException("팀장은 자신의 팀에 참가 신청할 수 없습니다.");
        }

        if (teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, user.getId(), TeamMember.Status.ACTIVE)) {
            throw new ConflictException("이미 참여 중인 팀입니다.");
        }

        if (teamApplicationRepository.existsByTeamIdAndUserIdAndStatus(teamId, user.getId(), TeamApplication.Status.PENDING)) {
            throw new ConflictException("이미 승인 대기 중인 참가 신청이 있습니다.");
        }

        TeamRecruitment recruitment = findRecruitment(teamId, request.roleCode());
        if (countFilledRecruitment(recruitment) >= recruitment.getRequiredCount()) {
            throw new IllegalArgumentException("해당 모집 분야의 정원이 마감되었습니다.");
        }

        TeamApplication application = teamApplicationRepository.save(
                TeamApplication.create(team.getId(), user.getId(), request.roleCode(), request.message())
        );

        List<TeamApplicationQuestion> questions = findActiveQuestions(team.getId());
        List<TeamApplicationAnswer> answers = saveApplicationAnswers(application.getId(), questions, request.answers());

        return TeamApplicationResponse.of(application, toAnswerResponses(questions, answers));
    }

    @Transactional
    public ApproveTeamApplicationResponse approveApplication(
            Long leaderUserId, Long teamId, Long applicationId, String roleCode
    ) {
        Team team = findTeamForUpdate(teamId);
        validateTeamLeader(team, leaderUserId);

        if (team.getStatus() != Team.Status.RECRUITING) {
            throw new IllegalArgumentException("모집 중인 팀의 신청만 승인할 수 있습니다.");
        }

        TeamApplication application = findPendingApplication(teamId, applicationId);
        TeamRecruitment recruitment = findRecruitment(teamId, roleCode);

        if (teamMemberRepository.existsByTeamIdAndUserIdAndStatus(teamId, application.getUserId(), TeamMember.Status.ACTIVE)) {
            throw new ConflictException("이미 참여 중인 사용자입니다.");
        }

        long activeMemberCount = teamMemberRepository.countByTeamIdAndStatus(teamId, TeamMember.Status.ACTIVE);
        if (team.getMaxMemberCount() != null && activeMemberCount >= team.getMaxMemberCount()) {
            throw new ConflictException("팀 정원이 이미 마감되었습니다.");
        }

        if (countFilledRecruitment(recruitment) >= recruitment.getRequiredCount()) {
            throw new IllegalArgumentException("해당 모집 분야의 정원이 이미 마감되었습니다.");
        }

        application.approve(roleCode);
        TeamMember teamMember = teamMemberRepository.save(
                TeamMember.createMember(teamId, application.getUserId(), roleCode)
        );

        return new ApproveTeamApplicationResponse(
                application.getId(),
                application.getTeamId(),
                application.getUserId(),
                application.getStatus(),
                roleCode,
                teamMember.getId()
        );
    }

    @Transactional
    public TeamApplicationResponse rejectApplication(
            Long leaderUserId, Long teamId, Long applicationId, String reason
    ) {
        Team team = findTeamForUpdate(teamId);
        validateTeamLeader(team, leaderUserId);

        TeamApplication application = findPendingApplication(teamId, applicationId);
        application.reject(reason);

        return TeamApplicationResponse.of(application, findAnswerResponses(application));
    }

    @Transactional(readOnly = true)
    public TeamApplicationListResponse getPendingApplications(Long leaderUserId, Long teamId) {
        Team team = findTeam(teamId);
        validateTeamLeader(team, leaderUserId);

        List<TeamApplication> applications = teamApplicationRepository.findAllByTeamIdAndStatus(
                teamId,
                TeamApplication.Status.PENDING
        );
        List<Long> applicationIds = applications.stream()
                .map(TeamApplication::getId)
                .toList();

        Map<Long, List<TeamApplicationAnswer>> answersByApplicationId = applicationIds.isEmpty()
                ? Map.of()
                : teamApplicationAnswerRepository.findAllByApplicationIdIn(applicationIds)
                        .stream()
                        .collect(Collectors.groupingBy(TeamApplicationAnswer::getApplicationId));

        List<TeamApplicationQuestion> questions = findActiveQuestions(teamId);
        List<TeamApplicationResponse> responses = applications.stream()
                .map(application -> TeamApplicationResponse.of(
                        application,
                        toAnswerResponses(
                                questions,
                                answersByApplicationId.getOrDefault(application.getId(), List.of())
                        )
                ))
                .toList();

        return new TeamApplicationListResponse(teamId, responses);
    }

    @Transactional(readOnly = true)
    public MyTeamApplicationListResponse getMyApplications(Long userId) {
        List<TeamApplicationResponse> applications = teamApplicationRepository
                .findAllByUserIdOrderByAppliedAtDesc(userId)
                .stream()
                .map(application -> TeamApplicationResponse.of(application, findAnswerResponses(application)))
                .toList();
        return new MyTeamApplicationListResponse(applications);
    }

    @Transactional
    public void cancelApplication(Long userId, Long teamId, Long applicationId) {
        findTeamForUpdate(teamId);
        TeamApplication application = findPendingApplication(teamId, applicationId);
        if (!application.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 참가 신청만 취소할 수 있습니다.");
        }
        application.cancel();
    }

    @Transactional
    public TeamDetailResponse closeRecruitment(Long leaderUserId, Long teamId) {
        Team team = findTeamForUpdate(teamId);
        validateTeamLeader(team, leaderUserId);
        if (team.getStatus() != Team.Status.RECRUITING) {
            throw new IllegalArgumentException("모집 중인 팀만 마감할 수 있습니다.");
        }
        team.closeRecruitment();
        return getTeamDetail(teamId);
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

    private Team findTeamForUpdate(Long teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("팀을 찾을 수 없습니다."));
    }

    private TeamRecruitment findRecruitment(Long teamId, String roleCode) {
        return teamRecruitmentRepository.findByTeamIdAndRoleCode(teamId, roleCode)
                .orElseThrow(() -> new IllegalArgumentException("해당 팀의 모집 분야가 아닙니다."));
    }

    private long countFilledRecruitment(TeamRecruitment recruitment) {
        return teamMemberRepository.countByTeamIdAndRoleCodeAndStatus(
                recruitment.getTeamId(), recruitment.getRoleCode(), TeamMember.Status.ACTIVE
        );
    }

    private TeamRecruitmentResponse toRecruitmentResponse(TeamRecruitment recruitment) {
        return TeamRecruitmentResponse.from(recruitment, countFilledRecruitment(recruitment));
    }

    private TeamApplication findPendingApplication(Long teamId, Long applicationId) {
        return teamApplicationRepository.findByIdAndTeamId(applicationId, teamId)
                .filter(application -> application.getStatus() == TeamApplication.Status.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("승인 대기 중인 참가 신청을 찾을 수 없습니다."));
    }

    private void validateTeamLeader(Team team, Long userId) {
        if (!team.getLeader().getId().equals(userId)) {
            throw new ForbiddenException("팀장만 참가 신청을 처리할 수 있습니다.");
        }
    }

    private List<TeamApplicationQuestionResponse> saveApplicationQuestions(
            Long teamId,
            List<CreateTeamQuestionRequest> questionRequests
    ) {
        if (questionRequests == null || questionRequests.isEmpty()) {
            return List.of();
        }

        List<TeamApplicationQuestion> questions = IntStream.range(0, questionRequests.size())
                .mapToObj(index -> {
                    CreateTeamQuestionRequest request = questionRequests.get(index);
                    return TeamApplicationQuestion.create(
                        teamId,
                        request.question(),
                        request.required(),
                        index + 1
                    );
                })
                .toList();

        return teamApplicationQuestionRepository.saveAll(questions)
                .stream()
                .map(TeamApplicationQuestionResponse::from)
                .toList();
    }

    private List<TeamRecruitmentResponse> saveRecruitments(
            Long teamId,
            List<CreateTeamRecruitmentRequest> recruitmentRequests
    ) {
        if (recruitmentRequests == null || recruitmentRequests.isEmpty()) {
            return List.of();
        }

        Set<String> requestedRoleCodes = recruitmentRequests.stream()
                .map(CreateTeamRecruitmentRequest::roleCode)
                .collect(Collectors.toSet());
        if (requestedRoleCodes.size() != recruitmentRequests.size()) {
            throw new IllegalArgumentException("같은 모집 분야를 중복 등록할 수 없습니다.");
        }

        Set<String> existingRoleCodes = profileRoleRepository.findAllByCodeIn(requestedRoleCodes)
                .stream()
                .map(ProfileRole::getCode)
                .collect(Collectors.toSet());
        if (!existingRoleCodes.equals(requestedRoleCodes)) {
            throw new IllegalArgumentException("존재하지 않는 모집 분야 코드가 포함되어 있습니다.");
        }

        List<TeamRecruitment> recruitments = recruitmentRequests.stream()
                .map(request -> TeamRecruitment.create(
                        teamId,
                        request.roleCode(),
                        request.requiredCount()
                ))
                .toList();

        return teamRecruitmentRepository.saveAll(recruitments)
                .stream()
                .map(TeamRecruitmentResponse::from)
                .toList();
    }

    private List<TeamApplicationAnswer> saveApplicationAnswers(
            Long applicationId,
            List<TeamApplicationQuestion> questions,
            List<TeamApplicationAnswerRequest> answerRequests
    ) {
        List<TeamApplicationAnswerRequest> normalizedAnswers =
                answerRequests == null ? List.of() : answerRequests;
        validateAnswers(questions, normalizedAnswers);

        Map<Long, TeamApplicationQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(TeamApplicationQuestion::getId, Function.identity()));

        List<TeamApplicationAnswer> answers = normalizedAnswers.stream()
                .map(request -> TeamApplicationAnswer.create(
                        applicationId,
                        request.questionId(),
                        request.answer()
                ))
                .filter(answer -> questionById.containsKey(answer.getQuestionId()))
                .toList();

        return teamApplicationAnswerRepository.saveAll(answers);
    }

    private void validateAnswers(
            List<TeamApplicationQuestion> questions,
            List<TeamApplicationAnswerRequest> answers
    ) {
        Set<Long> questionIds = questions.stream()
                .map(TeamApplicationQuestion::getId)
                .collect(Collectors.toSet());

        Set<Long> answeredQuestionIds = new HashSet<>();
        for (TeamApplicationAnswerRequest answer : answers) {
            if (!questionIds.contains(answer.questionId())) {
                throw new IllegalArgumentException("지원서에 없는 질문에 대한 답변입니다.");
            }
            if (!answeredQuestionIds.add(answer.questionId())) {
                throw new IllegalArgumentException("같은 질문에 여러 번 답변할 수 없습니다.");
            }
        }

        boolean hasMissingRequiredAnswer = questions.stream()
                .filter(TeamApplicationQuestion::isRequired)
                .map(TeamApplicationQuestion::getId)
                .anyMatch(questionId -> !answeredQuestionIds.contains(questionId));

        if (hasMissingRequiredAnswer) {
            throw new IllegalArgumentException("필수 지원서 질문에 답변해야 합니다.");
        }
    }

    private List<TeamApplicationQuestion> findActiveQuestions(Long teamId) {
        return teamApplicationQuestionRepository.findAllByTeamIdAndStatusOrderByDisplayOrderAsc(
                teamId,
                TeamApplicationQuestion.Status.ACTIVE
        );
    }

    private List<TeamApplicationAnswerResponse> findAnswerResponses(TeamApplication application) {
        List<TeamApplicationQuestion> questions = findActiveQuestions(application.getTeamId());
        List<TeamApplicationAnswer> answers = teamApplicationAnswerRepository.findAllByApplicationId(application.getId());
        return toAnswerResponses(questions, answers);
    }

    private List<TeamApplicationAnswerResponse> toAnswerResponses(
            List<TeamApplicationQuestion> questions,
            List<TeamApplicationAnswer> answers
    ) {
        Map<Long, TeamApplicationQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(TeamApplicationQuestion::getId, Function.identity()));

        return answers.stream()
                .filter(answer -> questionById.containsKey(answer.getQuestionId()))
                .map(answer -> TeamApplicationAnswerResponse.of(questionById.get(answer.getQuestionId()), answer))
                .sorted(Comparator.comparingInt(TeamApplicationAnswerResponse::displayOrder))
                .toList();
    }
}
