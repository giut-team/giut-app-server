package com.giut.server.notification.controller;

import com.giut.server.notification.dto.PushDeviceRequest;
import com.giut.server.notification.dto.PushDeviceResponse;
import com.giut.server.notification.service.PushDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@Tag(name = "Push Device")
@SecurityRequirement(name = "JWT")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/push-devices")
public class PushDeviceController {
    private final PushDeviceService devices;
    private final Clock clock;

    @PutMapping("/{installationId}")
    @Operation(summary = "푸시 기기 등록·갱신",
            description = "installationId는 앱 설치마다 앱이 만들어 보관하는 고유 값(8~100자, 영문·숫자·._:-)입니다. "
                    + "로그인 직후, 앱 실행 때, Expo 푸시 토큰이 바뀔 때 호출합니다. 같은 installationId는 한 행으로 갱신되고 "
                    + "다른 계정으로 로그인하면 소유자가 바뀝니다.")
    public ResponseEntity<PushDeviceResponse> register(@PathVariable String installationId,
                                                       @Valid @RequestBody PushDeviceRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(devices.register(Long.valueOf(authentication.getName()), installationId, request,
                clock.instant()));
    }

    @DeleteMapping("/{installationId}")
    @Operation(summary = "푸시 기기 해제", description = "로그아웃 직전(토큰을 지우기 전)에 호출합니다. "
            + "본인 기기만 비활성화하며, 없거나 다른 회원의 기기여도 204입니다.")
    public ResponseEntity<Void> unregister(@PathVariable String installationId, Authentication authentication) {
        devices.unregister(Long.valueOf(authentication.getName()), installationId, clock.instant());
        return ResponseEntity.noContent().build();
    }
}
