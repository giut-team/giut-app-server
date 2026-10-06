package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.dto.response.SeedSampleCompetitionsResponse;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.seed.SampleCompetitionCatalog;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompetitionSeedService {

    private final UserRepository userRepository;
    private final CompetitionUrlRepository competitionUrlRepository;
    private final CompetitionUrlNormalizer competitionUrlNormalizer;
    private final CompetitionService competitionService;
    private final SampleCompetitionCatalog sampleCompetitionCatalog;

    @Transactional
    public SeedSampleCompetitionsResponse seedSamples(Long adminUserId) {
        // Check the current DB role even when every sample already exists.
        userRepository.findById(adminUserId)
                .filter(user -> user.getRole() == User.Role.ADMIN && user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("관리자 권한이 필요합니다."));

        int createdCount = 0;
        int skippedCount = 0;
        List<Long> competitionIds = new ArrayList<>();

        for (UpsertCompetitionRequest sample : sampleCompetitionCatalog.competitions()) {
            String sourceHash = competitionUrlNormalizer.normalize(sample.urls().getFirst().url()).hash();
            Optional<CompetitionUrl> existing = competitionUrlRepository.findByNormalizedUrlHash(sourceHash);
            if (existing.isPresent()) {
                competitionIds.add(existing.get().getCompetition().getId());
                skippedCount++;
                continue;
            }

            // Reuse URL validation, normalized hashes and the administrator review log.
            competitionIds.add(competitionService.createByAdmin(adminUserId, sample).id());
            createdCount++;
        }

        return new SeedSampleCompetitionsResponse(createdCount, skippedCount, List.copyOf(competitionIds));
    }
}

