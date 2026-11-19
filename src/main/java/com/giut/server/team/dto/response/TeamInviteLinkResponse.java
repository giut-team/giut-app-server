package com.giut.server.team.dto.response;

import java.time.Instant;

public record TeamInviteLinkResponse(Long inviteLinkId, String roleCode, Instant expiresAt, String inviteUrl) {}
