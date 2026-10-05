package com.giut.server.profile.seed;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSkillTagCatalogTest {

    @Test
    void defaultsPassInputValidationHaveUniqueNormalizedNamesAndCoverTheSeedRoleCodes() throws Exception {
        Set<String> seedCodes = new HashSet<>();
        try (var input = new ClassPathResource("db/seed/profile_roles.sql").getInputStream()) {
            var pattern = Pattern.compile("\\('(DEVELOPMENT|DESIGN|PLANNING|MARKETING)', '([^']+)'");
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int insertStart = script.indexOf("INSERT INTO profile_roles ");
            assertThat(insertStart).isGreaterThanOrEqualTo(0);
            var matcher = pattern.matcher(script.substring(insertStart));
            while (matcher.find()) seedCodes.add(matcher.group(2));
        }
        assertThat(seedCodes).isNotEmpty();

        Set<String> names = new HashSet<>();
        Set<String> mappedCodes = new HashSet<>();
        var skills = new DefaultSkillTagCatalog().skills();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var skill : skills) {
                assertThat(validator.validate(skill)).as(skill.name()).isEmpty();
                assertThat(names.add(skill.name().trim().toLowerCase(Locale.ROOT))).isTrue();
                assertThat(skill.relatedRoleCodes()).doesNotHaveDuplicates();
                mappedCodes.addAll(skill.relatedRoleCodes());
            }
        }
        assertThat(mappedCodes).containsExactlyInAnyOrderElementsOf(seedCodes);
    }
}
