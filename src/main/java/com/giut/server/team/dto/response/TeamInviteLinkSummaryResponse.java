package com.giut.server.team.dto.response;

import java.time.Instant;

public record TeamInviteLinkSummaryResponse(Long inviteLinkId, String roleCode, Instant expiresAt) {}
