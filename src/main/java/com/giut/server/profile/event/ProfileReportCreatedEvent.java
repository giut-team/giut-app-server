package com.giut.server.profile.event;

import com.giut.server.profile.entity.ProfileReport;

import java.time.Instant;

/** Immutable notification metadata only; no JPA entity or user-submitted content. */
public record ProfileReportCreatedEvent(Long reportId, ProfileReport.Reason reason, Instant createdAt) {
}
