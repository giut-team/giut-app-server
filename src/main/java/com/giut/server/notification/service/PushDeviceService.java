package com.giut.server.notification.service;

import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.notification.dto.PushDeviceRequest;
import com.giut.server.notification.dto.PushDeviceResponse;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 앱 설치별 Expo 푸시 토큰 등록·해제. 앱은 로그인 직후, 앱 실행 때, 토큰이 바뀔 때 등록(upsert)하고 로그아웃 직전에 해제한다.
 * 해제하지 않으면 다음에 그 기기로 로그인한 다른 회원이 이전 회원의 알림을 받을 수 있다
 * (발송 작업자도 보내기 직전에 기기 소유자가 알림 수신자와 같은지 다시 확인한다).
 */
@Service
@RequiredArgsConstructor
public class PushDeviceService {
    private static final Pattern INSTALLATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

    private final PushDeviceRepository devices;
    private final UserRepository users;

    /**
     * 같은 회원의 동시 등록(로그인과 토큰 변경 이벤트가 겹치는 경우)은 회원 행 잠금으로 직렬화해 설치 id UNIQUE 충돌을 피한다.
     * 같은 토큰을 가진 다른 설치 행은 비활성화한다(재설치·계정 전환 뒤 남은 행으로 중복 발송하지 않는다).
     */
    @Transactional
    public PushDeviceResponse register(Long userId, String installationId, PushDeviceRequest request, Instant now) {
        requireInstallationId(installationId);
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        PushDevice device = devices.findByInstallationIdForUpdate(installationId)
                .map(found -> {
                    found.refresh(user, request.pushToken(), request.platform(), request.appVersion(), now);
                    return found;
                })
                .orElseGet(() -> devices.save(PushDevice.register(user, installationId, request.pushToken(),
                        request.platform(), request.appVersion(), now)));
        devices.findActiveByTokenExcept(request.pushToken(), installationId)
                .forEach(other -> other.deactivate(PushDevice.Reasons.TOKEN_REASSIGNED, now));
        return PushDeviceResponse.of(device);
    }

    /** 본인 기기만 비활성화한다. 없거나 다른 회원의 설치면 아무것도 하지 않는다(존재 여부를 드러내지 않는다). */
    @Transactional
    public void unregister(Long userId, String installationId, Instant now) {
        requireInstallationId(installationId);
        devices.findByInstallationIdForUpdate(installationId)
                .filter(device -> device.getUser().getId().equals(userId))
                .ifPresent(device -> device.deactivate(PushDevice.Reasons.LOGGED_OUT, now));
    }

    private static void requireInstallationId(String installationId) {
        if (installationId == null || !INSTALLATION_ID.matcher(installationId).matches()) {
            throw new IllegalArgumentException("installationId 형식이 올바르지 않습니다.");
        }
    }
}
