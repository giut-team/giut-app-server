package com.giut.server.performance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("perf")
@RequiredArgsConstructor
@EnableConfigurationProperties(PerformanceSeedProperties.class)
@ConditionalOnProperty(
        prefix = "app.performance.seed",
        name = "enabled",
        havingValue = "true"
)
public class PerformanceSeedRunner implements ApplicationRunner {

    private final PerformanceDataSeedService seedService;
    private final PerformanceSeedProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        PerformanceSeedResult result = seedService.generate(properties);

        if (!result.created()) {
            log.info(
                    "성능 측정 데이터가 이미 존재하여 생성을 건너뜁니다. measurementUserId={}",
                    result.measurementUserId()
            );
            return;
        }

        log.info(
                "성능 측정 데이터 생성 완료: measurementUserId={}, competitionId={}, "
                        + "users={}, teams={}, teamMembers={}, applications={}, "
                        + "chatRooms={}, messages={}",
                result.measurementUserId(),
                result.competitionId(),
                result.userCount(),
                result.teamCount(),
                result.teamMemberCount(),
                result.applicationCount(),
                result.chatRoomCount(),
                result.messageCount()
        );
    }
}
