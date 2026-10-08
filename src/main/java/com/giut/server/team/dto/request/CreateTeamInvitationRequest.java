package com.giut.server.team.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTeamInvitationRequest(
        @NotNull Long inviteeUserId,
        @NotBlank String roleCode,
        @Size(max = 500) String message
) {}