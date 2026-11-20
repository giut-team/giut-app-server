package com.giut.server.competition.dto.request;

import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 공모전 URL 목록은 최대 10개이고, 비어 있는(null) 원소는 500이 아니라 검증 오류(400)가 되어야 한다. */
class CompetitionUrlLimitValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void 관리자_등록은_URL_10개까지_허용하고_11개는_거부한다() {
        assertThat(urlViolations(validator.validate(create(urls(10))))).isEmpty();
        assertThat(urlViolations(validator.validate(create(urls(11))))).isNotEmpty();
    }

    @Test
    void 관리자_수정은_URL_10개까지_허용하고_11개는_거부한다() {
        assertThat(urlViolations(validator.validate(upsert(urls(10))))).isEmpty();
        assertThat(urlViolations(validator.validate(upsert(urls(11))))).isNotEmpty();
    }

    @Test
    void AI_등록은_URL_10개까지_허용하고_11개는_거부한다() {
        assertThat(urlViolations(validator.validate(ai(urls(10))))).isEmpty();
        assertThat(urlViolations(validator.validate(ai(urls(11))))).isNotEmpty();
    }

    @Test
    void 관리자_등록과_수정은_null_URL_원소를_검증_오류로_거부한다() {
        List<CompetitionUrlRequest> withNull = new ArrayList<>(urls(1));
        withNull.add(null);

        assertThat(urlViolations(validator.validate(create(withNull)))).isNotEmpty();
        assertThat(urlViolations(validator.validate(upsert(withNull)))).isNotEmpty();
    }

    private static List<CompetitionUrlRequest> urls(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new CompetitionUrlRequest(
                        CompetitionUrl.Type.RECRUITMENT, "https://example.com/recruitment/" + i, i == 0))
                .toList();
    }

    private static CreateCompetitionRequest create(List<CompetitionUrlRequest> urls) {
        return new CreateCompetitionRequest("공모전", Competition.Category.AI_DATA, "주최", "대학생", "요약",
                null, Instant.parse("2099-01-01T00:00:00Z"), Competition.PublicationStatus.DRAFT, urls);
    }

    private static UpsertCompetitionRequest upsert(List<CompetitionUrlRequest> urls) {
        return new UpsertCompetitionRequest("공모전", Competition.Category.AI_DATA, "주최", "대학생", "요약",
                null, Instant.parse("2099-01-01T00:00:00Z"), Competition.PublicationStatus.DRAFT, urls);
    }

    private static AiCompetitionRequest ai(List<CompetitionUrlRequest> urls) {
        return new AiCompetitionRequest("공모전", List.of(Competition.Category.AI_DATA), "주최",
                AiTargetParticipants.ALL, "요약", null, Instant.parse("2099-01-01T00:00:00Z"), urls,
                AiActivityType.CONTEST, null, null, null, null);
    }

    private static <T> List<ConstraintViolation<T>> urlViolations(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().startsWith("urls"))
                .toList();
    }
}
