package com.giut.server.competition.service;

import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.entity.CompetitionIngestionFailure;
import com.giut.server.competition.repository.CompetitionIngestionFailureRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCompetitionBatchServiceTest {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String ITEM = """
            {"title":"%s", "activityKind":"HACKATHON", "category":["IT_DEVELOP"], "targetParticipants":"ALL",
             "applicationStartAt":"2026-11-01", "applicationEndAt":"2026-12-01",
             "urls":[{"type":"OFFICIAL","url":"https://example.com/%s"}]}
            """;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Validator validator = VALIDATORS.getValidator();
    private CompetitionService competitionService;
    private CompetitionIngestionFailureRepository failureRepository;
    private AiCompetitionBatchService service;

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @BeforeEach
    void setUp() {
        competitionService = mock(CompetitionService.class);
        failureRepository = mock(CompetitionIngestionFailureRepository.class);
        service = new AiCompetitionBatchService(competitionService, failureRepository, objectMapper, validator);
    }

    @Test
    void unexpectedItemErrorIsRecordedAndLaterItemsContinue() {
        AdminCompetitionResponse saved = mock(AdminCompetitionResponse.class);
        when(competitionService.createByAi(any()))
                .thenThrow(new NullPointerException("boom"))
                .thenReturn(saved);

        assertThat(service.ingest(List.of(item("first"), item("second")))).containsExactly(saved);

        ArgumentCaptor<CompetitionIngestionFailure> captor = ArgumentCaptor.forClass(CompetitionIngestionFailure.class);
        verify(failureRepository).save(captor.capture());
        assertThat(captor.getValue().getFailureType()).isEqualTo(CompetitionIngestionFailure.FailureType.UNEXPECTED);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("NullPointerException: boom");
        assertThat(captor.getValue().getTitle()).isEqualTo("first");
    }

    @Test
    void infrastructureFailureStopsTheBatchWithoutTryingRemainingItems() {
        when(competitionService.createByAi(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.ingest(List.of(item("first"), item("second"))))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(competitionService, times(1)).createByAi(any());
        verifyNoInteractions(failureRepository);
    }

    @Test
    void failureRecordSaveErrorDoesNotStopRemainingItems() {
        AdminCompetitionResponse saved = mock(AdminCompetitionResponse.class);
        when(competitionService.createByAi(any()))
                .thenThrow(new IllegalArgumentException("부적합"))
                .thenReturn(saved);
        when(failureRepository.save(any())).thenThrow(new IllegalStateException("record failed"));

        assertThat(service.ingest(List.of(item("first"), item("second")))).containsExactly(saved);
        verify(competitionService, times(2)).createByAi(any());
    }

    @Test
    void validationErrorsAreRecordedBeforeCallingTheSaveTransaction() {
        JsonNode missingTitle = objectMapper.readTree(ITEM.formatted("", "blank-title"));

        assertThat(service.ingest(List.of(missingTitle))).isEmpty();

        verifyNoInteractions(competitionService);
        ArgumentCaptor<CompetitionIngestionFailure> captor = ArgumentCaptor.forClass(CompetitionIngestionFailure.class);
        verify(failureRepository).save(captor.capture());
        assertThat(captor.getValue().getFailureType()).isEqualTo(CompetitionIngestionFailure.FailureType.INVALID_FIELD);
        assertThat(captor.getValue().getErrorMessage()).startsWith("title:");
        assertThat(captor.getValue().getPrimaryUrl()).isEqualTo("https://example.com/blank-title");
    }

    private JsonNode item(String name) {
        return objectMapper.readTree(ITEM.formatted(name, name));
    }
}
