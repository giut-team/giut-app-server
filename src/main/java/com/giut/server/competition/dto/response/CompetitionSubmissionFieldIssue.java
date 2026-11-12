package com.giut.server.competition.dto.response;

import java.util.List;

public record CompetitionSubmissionFieldIssue(
        String field,
        String label,
        String type,
        List<String> options,
        String reason,
        String message
) {
}
