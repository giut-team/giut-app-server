package com.giut.server.competition.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCompetitionSubmissionRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String reason
) {
    public enum Decision { APPROVE, REJECT }
}
