package com.giut.server.team.dto.response;

import java.time.Instant;

public record TeamInvitePreviewResponse(Long teamId, String teamName, String roleCode, Instant expiresAt) {}
