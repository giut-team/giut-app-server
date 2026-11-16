package com.giut.server.performance;

import com.giut.server.chat.repository.ChatMessageRepository;
import com.giut.server.chat.repository.ChatRoomMemberRepository;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:performance_seed_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class PerformanceDataSeedServiceTest {

    @Autowired PerformanceDataSeedService seedService;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TeamMemberRepository teamMemberRepository;
    @Autowired TeamApplicationRepository teamApplicationRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    @Test
    void createsFixedTeamAndChatDatasetAndSkipsDuplicateRun() {
        PerformanceSeedProperties properties = properties();

        PerformanceSeedResult created = seedService.generate(properties);

        assertThat(created.created()).isTrue();
        assertThat(created.userCount()).isEqualTo(9);
        assertThat(created.teamCount()).isEqualTo(3);
        assertThat(created.teamMemberCount()).isEqualTo(9);
        assertThat(created.applicationCount()).isEqualTo(6);
        assertThat(created.chatRoomCount()).isEqualTo(2);
        assertThat(created.messageCount()).isEqualTo(8);

        assertThat(userRepository.count()).isEqualTo(9);
        assertThat(competitionRepository.count()).isEqualTo(1);
        assertThat(teamRepository.count()).isEqualTo(3);
        assertThat(teamMemberRepository.count()).isEqualTo(9);
        assertThat(teamApplicationRepository.count()).isEqualTo(6);
        assertThat(chatRoomRepository.count()).isEqualTo(2);
        assertThat(chatRoomMemberRepository.count()).isEqualTo(4);
        assertThat(chatMessageRepository.count()).isEqualTo(8);

        PerformanceSeedResult skipped = seedService.generate(properties);

        assertThat(skipped.created()).isFalse();
        assertThat(skipped.measurementUserId()).isEqualTo(created.measurementUserId());
        assertThat(userRepository.count()).isEqualTo(9);
        assertThat(teamRepository.count()).isEqualTo(3);
        assertThat(chatRoomRepository.count()).isEqualTo(2);
        assertThat(chatMessageRepository.count()).isEqualTo(8);
    }

    private PerformanceSeedProperties properties() {
        return new PerformanceSeedProperties(
                true,
                PerformanceSeedProperties.REQUIRED_CONFIRMATION,
                "perf-test-password",
                3,
                3,
                2,
                2,
                4,
                5
        );
    }
}
