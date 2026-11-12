package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CompetitionSearchRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.competition.repository.CompetitionScrapSummaryProjection;
import com.giut.server.competition.repository.CompetitionTeamRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitionRecruitmentDeadlineTest {

    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

    @Mock private CompetitionRepository competitionRepository;
    @Mock private CompetitionScrapRepository competitionScrapRepository;
    @Mock private CompetitionUrlRepository competitionUrlRepository;
    @Mock private CompetitionTeamRepository competitionTeamRepository;
    @InjectMocks private CompetitionService competitionService;

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void detailIncludesRecruitingTeamsAtAndAfterCompetitionDeadline(long offsetSeconds) {
        assertDetailIncludesTeam(publishedCompetition(NOW.plusSeconds(offsetSeconds)), "CLOSED");
    }

    @Test
    void detailIncludesTeamsJustBeforeDeadline() {
        assertDetailIncludesTeam(publishedCompetition(NOW.plusNanos(1)), "OPEN");
    }

    @Test
    void detailIncludesTeamsWithoutCompetitionDeadline() {
        assertDetailIncludesTeam(publishedCompetition(null), "OPEN");
    }

    @Test
    void listCountsRecruitingTeamsForReturnedCompetitions() {
        when(competitionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(competition(1L, NOW.plusSeconds(1)))));
        var count = teamCount(1L, 2L);
        when(competitionTeamRepository.countByCompetitionIdsAndStatus(List.of(1L), Team.Status.RECRUITING))
                .thenReturn(List.of(count));
        var response = atFixedNow(() -> competitionService.getCompetitions(searchRequest()));
        assertThat(response.competitions()).extracting("teamCount").containsExactly(2L);
    }

    @Test
    void emptyCompetitionPageSkipsTeamCountQuery() {
        when(competitionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Competition>(List.of()));

        var response = atFixedNow(() -> competitionService.getCompetitions(searchRequest()));

        assertThat(response.competitions()).isEmpty();
        verifyNoInteractions(competitionTeamRepository);
    }

    @Test
    void top5CountsRecruitingTeamsForReturnedCompetitions() {
        when(competitionRepository.findTop5ByPopularity(Competition.PublicationStatus.PUBLISHED.name(), NOW))
                .thenReturn(List.of(competition(1L, NOW.plusSeconds(1)), competition(2L, NOW.plusSeconds(2))));
        var counts = List.of(teamCount(1L, 2L), teamCount(2L, 1L));
        when(competitionTeamRepository.countByCompetitionIdsAndStatus(List.of(1L, 2L), Team.Status.RECRUITING))
                .thenReturn(counts);

        var response = atFixedNow(competitionService::getTop5Competitions);

        assertThat(response).extracting("teamCount").containsExactly(2L, 1L);
    }

    @Test
    void closingSoonCountsOnlyRecruitingTeams() {
        when(competitionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(competition(1L, NOW.plusSeconds(1))));
        var count = teamCount(1L, 2L);
        when(competitionTeamRepository.countByCompetitionIdsAndStatus(List.of(1L), Team.Status.RECRUITING))
                .thenReturn(List.of(count));

        var response = atFixedNow(competitionService::getClosingSoonCompetitions);

        assertThat(response).extracting("teamCount").containsExactly(2L);
    }

    private void assertDetailIncludesTeam(Competition competition, String competitionRecruitmentStatus) {
        var teamRow = mock(CompetitionTeamRepository.RecruitingTeamWithActiveMemberCount.class);
        when(teamRow.getTeamId()).thenReturn(3L);
        when(teamRow.getName()).thenReturn("모집 중인 팀");
        when(teamRow.getLeaderId()).thenReturn(12L);
        when(teamRow.getDescription()).thenReturn("팀 소개");
        when(teamRow.getMaxMemberCount()).thenReturn((short) 5);
        when(teamRow.getStatus()).thenReturn(Team.Status.RECRUITING);
        when(teamRow.getActiveMemberCount()).thenReturn(0L);
        when(competitionTeamRepository.findWithActiveMemberCount(1L, Team.Status.RECRUITING, TeamMember.Status.ACTIVE))
                .thenReturn(List.of(teamRow));
        when(competitionTeamRepository.countByCompetition_Id(1L)).thenReturn(1L);

        var response = atFixedNow(() -> competitionService.getPublishedCompetition(12L, 1L));

        assertThat(response.teams()).extracting("teamId").containsExactly(3L);
        assertThat(response.teamCount()).isEqualTo(1);
        assertThat(response.recruitingTeamCount()).isEqualTo(1);
        assertThat(response.recruitmentStatus().name()).isEqualTo(competitionRecruitmentStatus);
        verify(competitionTeamRepository)
                .findWithActiveMemberCount(1L, Team.Status.RECRUITING, TeamMember.Status.ACTIVE);
    }

    private CompetitionSearchRequest searchRequest() {
        return new CompetitionSearchRequest(null, null, null, null, null);
    }

    private Competition publishedCompetition(Instant deadline) {
        Competition competition = competition(1L, deadline);
        when(competitionRepository.findByIdAndPublicationStatus(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(competition));
        when(competitionRepository.incrementViewCount(1L, Competition.PublicationStatus.PUBLISHED))
                .thenReturn(1);
        var scrapSummary = mock(CompetitionScrapSummaryProjection.class);
        when(scrapSummary.getScrapCount()).thenReturn(0L);
        when(scrapSummary.isScrapped()).thenReturn(false);
        when(competitionScrapRepository.findScrapSummary(12L, 1L)).thenReturn(scrapSummary);
        return competition;
    }

    private Competition competition(Long id, Instant deadline) {
        Competition competition = Competition.create(
                "공모전", Competition.Category.WEB_MOBILE_IT, "서울특별시", "대학생", "공모전 소개",
                NOW.minusSeconds(3600), deadline, Competition.PublicationStatus.PUBLISHED,
                Competition.VerificationStatus.MANUALLY_VERIFIED, null);
        ReflectionTestUtils.setField(competition, "id", id);
        return competition;
    }

    private CompetitionTeamRepository.CompetitionTeamCount teamCount(Long competitionId, long count) {
        var result = mock(CompetitionTeamRepository.CompetitionTeamCount.class);
        when(result.getCompetitionId()).thenReturn(competitionId);
        when(result.getTeamCount()).thenReturn(count);
        return result;
    }

    private <T> T atFixedNow(Supplier<T> action) {
        try (MockedStatic<Instant> time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(NOW);
            return action.get();
        }
    }
}
