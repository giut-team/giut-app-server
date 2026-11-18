package com.giut.server.notification.dto;

import com.giut.server.notification.entity.PushDevice;

import java.time.Instant;

public record PushDeviceResponse(String installationId, PushDevice.Platform platform, boolean active, Instant lastSeenAt) {

    public static PushDeviceResponse of(PushDevice device) {
        return new PushDeviceResponse(device.getInstallationId(), device.getPlatform(), device.isActive(), device.getLastSeenAt());
    }
}
