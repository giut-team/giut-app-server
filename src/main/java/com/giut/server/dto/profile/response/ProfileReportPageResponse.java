package com.giut.server.dto.profile.response;

import com.giut.server.entity.ProfileReport;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProfileReportPageResponse(
        List<AdminProfileReportSummaryResponse> reports,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ProfileReportPageResponse from(Page<ProfileReport> reports) {
        return new ProfileReportPageResponse(
                reports.getContent().stream().map(AdminProfileReportSummaryResponse::from).toList(),
                reports.getNumber(),
                reports.getSize(),
                reports.getTotalElements(),
                reports.getTotalPages()
        );
    }
}
