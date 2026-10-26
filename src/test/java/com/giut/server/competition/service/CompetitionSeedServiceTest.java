package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.CreateCompetitionRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.seed.SampleCompetitionCatalog;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitionSeedServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CompetitionService competitionService;

    private final CompetitionUrlNormalizer normalizer = new CompetitionUrlNormalizer();
    private final SampleCompetitionCatalog catalog = new SampleCompetitionCatalog();
    private CompetitionSeedService service;

    @BeforeEach
    void setUp() {
        service = new CompetitionSeedService(
                userRepository, competitionService, catalog
        );
    }

    @Test
    void everyCallCreatesTenNewIdsAndDistinctUrlHashesEvenWithIdenticalContent() {
        Map<String, CompetitionUrl> saved = new HashMap<>();
        stubAdminAndStorage(saved);

        var first = service.seedSamples(10L);
        var second = service.seedSamples(10L);

        assertThat(first.createdCount()).isEqualTo(10);
        assertThat(first.skippedCount()).isZero();
        assertThat(first.competitionIds()).hasSize(10).doesNotHaveDuplicates();
        assertThat(second.createdCount()).isEqualTo(10);
        assertThat(second.skippedCount()).isZero();
        assertThat(second.competitionIds()).hasSize(10).doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(first.competitionIds());
        assertThat(saved).hasSize(20);
        verify(competitionService, times(20)).createByAdmin(eq(10L), any());
    }

    @Test
    void eachBatchUsesOneSharedThirtyDayPeriodAndNeverUpdatesOrPublishesExistingData() {
        Map<String, CompetitionUrl> saved = new HashMap<>();
        stubAdminAndStorage(saved);

        var before = java.time.Instant.now();
        var response = service.seedSamples(10L);
        var after = java.time.Instant.now();

        assertThat(response.createdCount()).isEqualTo(10);
        assertThat(response.skippedCount()).isZero();
        var start = saved.values().iterator().next().getCompetition().getApplicationStartAt();
        assertThat(start).isBetween(before, after);
        assertThat(saved.values()).allSatisfy(url -> {
            assertThat(url.getCompetition().getApplicationStartAt()).isEqualTo(start);
            assertThat(url.getCompetition().getApplicationEndAt()).isEqualTo(start.plus(java.time.Duration.ofDays(30)));
        });
        verify(competitionService, times(10)).createByAdmin(eq(10L), any());
        verify(competitionService, never()).updateByAdmin(any(), any(), any());
        verify(competitionService, never()).publishByAdmin(any(), any());
    }

    @Test
    void studentCannotRegisterSamplesEvenIfTokenPreviouslyHadAdminRole() {
        User student = admin();
        ReflectionTestUtils.setField(student, "role", User.Role.STUDENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));

        assertDeniedWithoutWrites();
    }

    @Test
    void suspendedAdminCannotRegisterSamples() {
        User suspended = admin();
        ReflectionTestUtils.setField(suspended, "status", User.Status.SUSPENDED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(suspended));

        assertDeniedWithoutWrites();
    }

    @Test
    void deletedAccountCannotRegisterSamples() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertDeniedWithoutWrites();
    }

    @Test
    void registrationFailureIsPropagatedAndRemainingSamplesAreNotAttempted() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin()));
        when(competitionService.createByAdmin(eq(10L), any())).thenAnswer(invocation -> {
            CreateCompetitionRequest request = invocation.getArgument(1);
            if (request.title().equals(catalog.competitions().get(2).title())) {
                throw new IllegalStateException("DB 등록 실패");
            }
            return AdminCompetitionResponse.from(competition(1L, request), List.of());
        });

        assertThatThrownBy(() -> service.seedSamples(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 등록 실패");
        verify(competitionService, times(3)).createByAdmin(eq(10L), any());
    }

    private void assertDeniedWithoutWrites() {
        assertThatThrownBy(() -> service.seedSamples(10L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(competitionService);
    }

    private void stubAdminAndStorage(Map<String, CompetitionUrl> saved) {
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin()));
        AtomicLong nextId = new AtomicLong(100L);
        when(competitionService.createByAdmin(eq(10L), any())).thenAnswer(invocation -> {
            CreateCompetitionRequest request = invocation.getArgument(1);
            Competition competition = competition(nextId.incrementAndGet(), request);
            var source = request.urls().getFirst();
            var normalized = normalizer.normalize(source.url());
            CompetitionUrl url = CompetitionUrl.create(
                    competition, source.type(), source.url(), normalized.value(), normalized.hash(), true
            );
            assertThat(saved.putIfAbsent(normalized.hash(), url)).as("URL hash must remain unique").isNull();
            return AdminCompetitionResponse.from(competition, List.of());
        });
    }

    private Competition competition(Long id, CreateCompetitionRequest request) {
        Competition competition = Competition.create(
                request.title(), request.category(), request.hostOrganization(), request.targetParticipants(),
                request.summary(), request.applicationStartAt(), request.applicationEndAt(),
                request.publicationStatus(), Competition.VerificationStatus.MANUALLY_VERIFIED, admin()
        );
        ReflectionTestUtils.setField(competition, "id", id);
        return competition;
    }

    private User admin() {
        return User.createAdmin("admin@example.com", "password-hash", "관리자");
    }
}

