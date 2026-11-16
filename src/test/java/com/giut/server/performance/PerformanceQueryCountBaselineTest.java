package com.giut.server.performance;

import com.giut.server.chat.dto.response.ChatMessageListResponse;
import com.giut.server.chat.dto.response.ChatRoomListResponse;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.chat.service.ChatService;
import com.giut.server.team.dto.response.TeamDetailResponse;
import com.giut.server.team.dto.response.TeamPageResponse;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.team.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("perf")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:performance_query_baseline;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.performance.sql-counter.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=",
        "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF"
})
class PerformanceQueryCountBaselineTest {

    private static final Path REPORT_PATH = Path.of(
            "build",
            "reports",
            "performance",
            "sql-query-baseline.md"
    );

    @Autowired PerformanceDataSeedService seedService;
    @Autowired SqlStatementCounter sqlStatementCounter;
    @Autowired TeamService teamService;
    @Autowired ChatService chatService;
    @Autowired TeamRepository teamRepository;
    @Autowired ChatRoomRepository chatRoomRepository;

    @Test
    void recordsCurrentSqlStatementCountsForCoreTeamAndChatQueries() throws IOException {
        PerformanceSeedProperties dataset = baselineDataset();
        PerformanceSeedResult seed = seedService.generate(dataset);
        Long teamId = teamRepository.findAll().getFirst().getId();
        Long chatRoomId = chatRoomRepository.findAll().getFirst().getId();

        warmUp(seed.measurementUserId(), seed.competitionId(), teamId, chatRoomId);

        var teamList = sqlStatementCounter.measure(
                "GET /api/teams?competitionId={competitionId}&page=0&size=10",
                () -> teamService.getRecruitingTeams(
                        seed.measurementUserId(),
                        seed.competitionId(),
                        0,
                        10
                )
        );
        var teamDetail = sqlStatementCounter.measure(
                "GET /api/teams/{teamId}",
                () -> teamService.getTeamDetail(teamId, seed.measurementUserId())
        );
        var chatRoomList = sqlStatementCounter.measure(
                "GET /api/chat-rooms",
                () -> chatService.getChatRooms(seed.measurementUserId())
        );
        var messageList = sqlStatementCounter.measure(
                "GET /api/chat-rooms/{chatRoomId}/messages?size=30",
                () -> chatService.getMessages(
                        seed.measurementUserId(),
                        chatRoomId,
                        null,
                        30
                )
        );

        assertThat(teamList.result().teams()).hasSize(10);
        assertThat(teamDetail.result().teamId()).isEqualTo(teamId);
        assertThat(chatRoomList.result().chatRooms()).hasSize(dataset.chatRoomCount());
        assertThat(messageList.result().messages()).hasSize(30);
        assertThat(chatRoomList.measurement().sqlStatementCount())
                .as("채팅방 수와 관계없이 목록 조회는 단일 SQL로 실행되어야 한다")
                .isEqualTo(1L);

        List<SqlStatementMeasurement> measurements = List.of(
                teamList.measurement(),
                teamDetail.measurement(),
                chatRoomList.measurement(),
                messageList.measurement()
        );
        assertThat(measurements)
                .allSatisfy(measurement -> {
                    assertThat(measurement.sqlStatementCount()).isPositive();
                    assertThat(measurement.durationMillis()).isPositive();
                });

        writeReport(dataset, measurements);
    }

    private void warmUp(Long userId, Long competitionId, Long teamId, Long chatRoomId) {
        teamService.getRecruitingTeams(userId, competitionId, 0, 10);
        teamService.getTeamDetail(teamId, userId);
        chatService.getChatRooms(userId);
        chatService.getMessages(userId, chatRoomId, null, 30);
    }

    private void writeReport(
            PerformanceSeedProperties dataset,
            List<SqlStatementMeasurement> measurements
    ) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        StringBuilder report = new StringBuilder()
                .append("# 팀·채팅 API SQL 기준 측정\n\n")
                .append("> H2 MySQL 호환 모드에서 쿼리 구조를 확인한 개발용 기준값입니다. ")
                .append("최종 성능 수치는 별도 MySQL 성능 DB와 k6로 측정합니다.\n\n")
                .append("## 데이터 규모\n\n")
                .append("- 팀: ").append(dataset.teamCount()).append("개\n")
                .append("- 팀별 활성 팀원: ").append(dataset.teamMembersPerTeam()).append("명\n")
                .append("- 팀별 지원: ").append(dataset.applicationsPerTeam()).append("개\n")
                .append("- 개인 채팅방: ").append(dataset.chatRoomCount()).append("개\n")
                .append("- 채팅방별 메시지: ").append(dataset.messagesPerRoom()).append("개\n\n")
                .append("## 측정 결과\n\n")
                .append("| 시나리오 | SQL 문장 수 | Hibernate 쿼리 수 | 서비스 처리 시간(ms) |\n")
                .append("|---|---:|---:|---:|\n");

        for (SqlStatementMeasurement measurement : measurements) {
            report.append("| `")
                    .append(measurement.scenario())
                    .append("` | ")
                    .append(measurement.sqlStatementCount())
                    .append(" | ")
                    .append(measurement.hibernateQueryCount())
                    .append(" | ")
                    .append("%.3f".formatted(measurement.durationMillis()))
                    .append(" |\n");
        }

        Files.writeString(REPORT_PATH, report.toString());
    }

    private PerformanceSeedProperties baselineDataset() {
        return new PerformanceSeedProperties(
                true,
                PerformanceSeedProperties.REQUIRED_CONFIRMATION,
                "perf-baseline-password",
                12,
                5,
                4,
                10,
                40,
                50
        );
    }
}
