package com.giut.server.global.alert;

import com.giut.server.global.alert.DiscordAlertService.TestAlertResult;
import com.giut.server.global.dto.ResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/alerts/discord")
@ConditionalOnProperty(name = "APP_ENVIRONMENT", havingValue = "dev")
public class DiscordAlertTestController {

    private final DiscordAlertService discordAlertService;

    public DiscordAlertTestController(DiscordAlertService discordAlertService) {
        this.discordAlertService = discordAlertService;
    }

    @PostMapping("/test")
    public ResponseEntity<ResultDto> sendTestAlert() {
        TestAlertResult result = discordAlertService.sendTestAlert();
        return switch (result) {
            case SENT -> response(HttpStatus.OK, true, "Discord 테스트 알림을 전송했습니다.");
            case NOT_CONFIGURED -> response(HttpStatus.SERVICE_UNAVAILABLE, false,
                    "Discord 알림이 서버에 설정되어 있지 않습니다.");
            case RATE_LIMITED -> response(HttpStatus.TOO_MANY_REQUESTS, false,
                    "테스트 알림은 30초에 한 번만 보낼 수 있습니다.");
            case FAILED -> response(HttpStatus.BAD_GATEWAY, false,
                    "Discord 전송에 실패했습니다. 서버의 비밀값을 출력하지 않는 범위에서 설정과 로그를 확인하세요.");
        };
    }

    private ResponseEntity<ResultDto> response(HttpStatus status, boolean success, String message) {
        return ResponseEntity.status(status).body(ResultDto.builder()
                .success(success)
                .message(message)
                .code(status.value())
                .build());
    }
}
