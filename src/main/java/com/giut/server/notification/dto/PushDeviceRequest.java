package com.giut.server.notification.dto;

import com.giut.server.notification.entity.PushDevice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param pushToken  expo-notifications {@code getExpoPushTokenAsync()}가 준 Expo 푸시 토큰.
 * @param appVersion 선택. 문제 기기 추적용.
 */
public record PushDeviceRequest(
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\]\\s]{1,200}\\]$", message = "Expo 푸시 토큰 형식이 아닙니다.")
        String pushToken,
        @NotNull PushDevice.Platform platform,
        @Size(max = 40) String appVersion) {
}
