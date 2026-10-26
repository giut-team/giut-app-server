package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.dto.request.CreateCompetitionRequest;
import com.giut.server.competition.dto.response.SeedSampleCompetitionsResponse;
import com.giut.server.competition.seed.SampleCompetitionCatalog;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompetitionSeedService {

    private final UserRepository userRepository;
    private final CompetitionService competitionService;
    private final SampleCompetitionCatalog sampleCompetitionCatalog;

    @Transactional
    public SeedSampleCompetitionsResponse seedSamples(Long adminUserId) {
        // Recheck the current DB role rather than trusting an old administrator token.
        userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == User.Role.ADMIN && user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("관리자 권한이 필요합니다."));

        int createdCount = 0;
        List<Long> competitionIds = new ArrayList<>();

        for (UpsertCompetitionRequest sample : sampleCompetitionCatalog.competitions()) {
            // Reuse URL validation, normalized hashes and the administrator review log.
            competitionIds.add(competitionService.createByAdmin(adminUserId, CreateCompetitionRequest.from(sample)).id());
            createdCount++;
        }

        // Retain the response field for compatibility; this API no longer skips existing samples.
        return new SeedSampleCompetitionsResponse(createdCount, 0, List.copyOf(competitionIds));
    }
}

