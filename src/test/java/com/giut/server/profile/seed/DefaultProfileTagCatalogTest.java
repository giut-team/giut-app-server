package com.giut.server.profile.seed;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProfileTagCatalogTest {

    @Test
    void nonSkillDefaultsMatchExistingSeedAndHaveUniqueNormalizedNamesPerType() throws Exception {
        DefaultProfileTagCatalog catalog = new DefaultProfileTagCatalog();
        List<String> interests = new ArrayList<>();
        List<String> experiences = new ArrayList<>();
        try (var input = new ClassPathResource("db/seed/profile_tags.sql").getInputStream()) {
            var matcher = Pattern.compile("\\('(INTEREST|EXPERIENCE)', '([^']+)'")
                    .matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            while (matcher.find()) {
                ("INTEREST".equals(matcher.group(1)) ? interests : experiences).add(matcher.group(2));
            }
        }
        assertThat(catalog.interests()).hasSize(5).containsExactlyElementsOf(interests);
        assertThat(catalog.experiences()).hasSize(8).containsExactlyElementsOf(experiences);
        for (var names : List.of(catalog.interests(), catalog.experiences())) {
            assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank().hasSizeLessThanOrEqualTo(80));
            assertThat(names.stream().map(name -> name.trim().toLowerCase(Locale.ROOT)).toList())
                    .doesNotHaveDuplicates();
        }
    }
}
