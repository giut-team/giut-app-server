package com.giut.server.team.service;

import com.giut.server.chat.entity.ChatMessage;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.team.dto.request.CreateTeamRequest;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_creation_deadline;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=",
        "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF"
})
class TeamCreationDeadlinePersistenceTest {

    @Autowired TeamService teamService;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TeamMemberRepository teamMemberRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    @Test
    void closedCompetitionRejectsNewTeamWithoutChangingExistingTeamOrChat() {
        User existingLeader = userRepository.save(
                User.createAdmin("deadline-existing@example.com", "encoded", "기존팀장")
        );
        User newLeader = userRepository.save(
                User.createAdmin("deadline-new@example.com", "encoded", "신규팀장")
        );
        Competition competition = competitionRepository.save(
                Competition.create(
                        "마감된 공모전",
                        Competition.Category.WEB_MOBILE_IT,
                        "테스트 주최사",
                        "대학생",
                        "마감 이후 팀 생성 차단 테스트",
                        Instant.now().minus(30, ChronoUnit.DAYS),
                        Instant.now().minus(1, ChronoUnit.DAYS),
                        Competition.PublicationStatus.PUBLISHED,
                        Competition.VerificationStatus.MANUALLY_VERIFIED,
                        existingLeader
                )
        );
        Team existingTeam = teamRepository.save(
                Team.create(
                        competition,
                        existingLeader,
                        "기존 팀",
                        "마감 전에 생성된 팀",
                        Team.ActivityMode.HYBRID,
                        (short) 4,
                        (short) 1,
                        Team.MeetingPlace.CAMPUS
                )
        );
        teamMemberRepository.save(TeamMember.createLeader(existingTeam.getId(), existingLeader.getId()));
        ChatRoom existingChatRoom = chatRoomRepository.save(
                ChatRoom.createTeamRoom(existingTeam.getId())
        );
        ChatMessage existingMessage = chatMessageRepository.save(
                ChatMessage.createText(
                        existingChatRoom.getId(),
                        existingLeader.getId(),
                        "마감 전에 작성된 메시지"
                )
        );

        long teamCountBefore = teamRepository.count();
        long memberCountBefore = teamMemberRepository.count();
        long chatRoomCountBefore = chatRoomRepository.count();
        long messageCountBefore = chatMessageRepository.count();

        assertThatThrownBy(() -> teamService.createTeam(
                newLeader.getId(),
                request(competition.getId())
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("모집이 마감된 공모전에는 팀을 생성할 수 없습니다.");

        assertThat(teamRepository.count()).isEqualTo(teamCountBefore);
        assertThat(teamMemberRepository.count()).isEqualTo(memberCountBefore);
        assertThat(chatRoomRepository.count()).isEqualTo(chatRoomCountBefore);
        assertThat(chatMessageRepository.count()).isEqualTo(messageCountBefore);
        assertThat(teamRepository.findById(existingTeam.getId())).isPresent();
        assertThat(chatRoomRepository.findById(existingChatRoom.getId())).isPresent();
        assertThat(chatMessageRepository.findById(existingMessage.getId())).isPresent();
    }

    private CreateTeamRequest request(Long competitionId) {
        return new CreateTeamRequest(
                competitionId,
                "새 팀",
                "마감 후 생성 시도",
                Team.ActivityMode.ONLINE,
                (short) 4,
                (short) 1,
                null,
                List.of(),
                List.of(),
                "BACKEND_DEVELOPER",
                List.of(Team.CollaborationTool.DISCORD)
        );
    }
}
