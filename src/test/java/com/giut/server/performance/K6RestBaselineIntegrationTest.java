package com.giut.server.performance;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_K6", matches = "true")
@ActiveProfiles("perf")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:k6_rest_baseline;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.jpa.properties.hibernate.generate_statistics=false",
                "app.performance.sql-counter.enabled=false",
                "app.performance.seed.enabled=false",
                "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "DISCORD_ALERT_WEBHOOK_URL=",
                "DISCORD_SLOW_API_WEBHOOK_URL=",
                "logging.level.org.hibernate.SQL=OFF"
        }
)
class K6RestBaselineIntegrationTest {

    private static final Path SCRIPT_PATH = Path.of(
            "performance", "k6", "rest-baseline.js"
    ).toAbsolutePath();
    private static final Path REPORT_DIRECTORY = Path.of(
            "build", "reports", "performance", "k6"
    ).toAbsolutePath();

    @LocalServerPort int port;

    @Autowired PerformanceDataSeedService seedService;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired ChatRoomRepository chatRoomRepository;

    @Test
    void measuresCoreTeamAndChatEndpointsWithK6() throws Exception {
        PerformanceSeedProperties dataset = baselineDataset();
        PerformanceSeedResult seed = seedService.generate(dataset);
        User measurementUser = userRepository.findById(seed.measurementUserId()).orElseThrow();
        Long teamId = teamRepository.findAllByCompetition_IdOrderByIdDesc(seed.competitionId())
                .getFirst()
                .getId();
        Long chatRoomId = chatRoomRepository.findAll().getFirst().getId();
        String accessToken = jwtProvider.generateAccessToken(measurementUser);

        Files.createDirectories(REPORT_DIRECTORY);
        for (String target : List.of(
                "team-list",
                "team-detail",
                "chat-room-list",
                "message-list"
        )) {
            runK6(target, seed.competitionId(), teamId, chatRoomId, accessToken);
        }
    }

    private void runK6(
            String target,
            Long competitionId,
            Long teamId,
            Long chatRoomId,
            String accessToken
    ) throws IOException, InterruptedException {
        Path reportPath = REPORT_DIRECTORY.resolve(target + "-baseline.md");
        Path logPath = REPORT_DIRECTORY.resolve(target + "-k6.log");

        ProcessBuilder processBuilder = new ProcessBuilder(
                "k6", "run", SCRIPT_PATH.toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("TARGET", target);
        processBuilder.environment().put("BASE_URL", "http://127.0.0.1:" + port);
        processBuilder.environment().put("ACCESS_TOKEN", accessToken);
        processBuilder.environment().put("COMPETITION_ID", competitionId.toString());
        processBuilder.environment().put("TEAM_ID", teamId.toString());
        processBuilder.environment().put("CHAT_ROOM_ID", chatRoomId.toString());
        processBuilder.environment().put("RESULT_PATH", reportPath.toString());
        processBuilder.environment().put("K6_VUS", "10");
        processBuilder.environment().put("K6_WARMUP_SECONDS", "5");
        processBuilder.environment().put("K6_MEASURE_SECONDS", "15");

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        Files.writeString(logPath, output);

        assertThat(exitCode)
                .withFailMessage("k6 target=%s 실행 실패%n%s", target, output)
                .isZero();
        assertThat(reportPath).exists();
    }

    private PerformanceSeedProperties baselineDataset() {
        return new PerformanceSeedProperties(
                true,
                PerformanceSeedProperties.REQUIRED_CONFIRMATION,
                "k6-baseline-password",
                100,
                5,
                10,
                50,
                200,
                250
        );
    }
}
