package com.giut.server.competition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompetitionSubmissionRequest(
        @NotBlank @Size(max = 2000) String sourceUrl,
        CompetitionSubmissionForm draft
) {
}
