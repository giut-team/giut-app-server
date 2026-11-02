package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record AiCompetitionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Competition.Category category,
        @Size(max = 150) String hostOrganization,
        @NotBlank @Size(max = 3000) String targetParticipants,
        @Size(max = 10000) String summary,
        @NotNull Instant applicationStartAt,
        @NotNull Instant applicationEndAt,
        @NotEmpty List<@NotNull @Valid CompetitionUrlRequest> urls
) {}
