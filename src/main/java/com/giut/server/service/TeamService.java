package com.giut.server.service;

import com.giut.server.dto.team.request.CreateTeamRequest;
import com.giut.server.dto.team.request.ApplyTeamRequest;
import com.giut.server.dto.team.request.CreateTeamQuestionRequest;
import com.giut.server.dto.team.request.TeamApplicationAnswerRequest;
import com.giut.server.dto.team.response.ApproveTeamApplicationResponse;
import com.giut.server.dto.team.response.CreateTeamResponse;
import com.giut.server.dto.team.response.TeamApplicationAnswerResponse;
import com.giut.server.dto.team.response.TeamApplicationListResponse;
import com.giut.server.dto.team.response.TeamApplicationQuestionResponse;
import com.giut.server.dto.team.response.TeamApplicationResponse;
import com.giut.server.entity.Competition;
import com.giut.server.entity.Team;
import com.giut.server.entity.TeamApplicationAnswer;
import com.giut.server.entity.TeamApplicationQuestion;
import com.giut.server.entity.TeamApplication;
import com.giut.server.entity.TeamMember;
import com.giut.server.entity.User;
import com.giut.server.exception.ResourceNotFoundException;
import com.giut.server.repository.CompetitionRepository;
import com.giut.server.repository.TeamApplicationAnswerRepository;
import com.giut.server.repository.TeamApplicationQuestionRepository;
import com.giut.server.repository.TeamApplicationRepository;
import com.giut.server.repository.TeamMemberRepository;
import com.giut.server.repository.TeamRepository;
import com.giut.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TeamMemberRepository teamMemberRepository;
    private final TeamApplicationRepository teamApplicationRepository;
    private final TeamApplicationQuestionRepository teamApplicationQuestionRepository;
    private final TeamApplicationAnswerRepository teamApplicationAnswerRepository;
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

        List<TeamApplicationQuestionResponse> applicationQuestions = saveApplicationQuestions(
                team.getId(),
                request.applicationQuestions()
        );

        return CreateTeamResponse.of(team, applicationQuestions);
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

        List<TeamApplicationQuestion> questions = findActiveQuestions(team.getId());
        List<TeamApplicationAnswer> answers = saveApplicationAnswers(application.getId(), questions, request.answers());

        return TeamApplicationResponse.of(application, toAnswerResponses(questions, answers));
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

        application.approve();
        TeamMember teamMember = teamMemberRepository.save(TeamMember.createMember(teamId, application.getUserId()));

        return new ApproveTeamApplicationResponse(
                application.getId(),
                application.getTeamId(),
                application.getUserId(),
                application.getStatus(),
                teamMember.getId()
        );
    }

    @Transactional
    public TeamApplicationResponse rejectApplication(Long leaderUserId, Long teamId, Long applicationId) {
        Team team = findTeam(teamId);
        validateTeamLeader(team, leaderUserId);

        TeamApplication application = findPendingApplication(teamId, applicationId);
        application.reject();

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
