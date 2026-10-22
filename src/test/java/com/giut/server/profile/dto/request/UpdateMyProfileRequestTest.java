package com.giut.server.profile.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateMyProfileRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acceptsProfileUpdateWithoutDepartmentNicknameOrGender() throws Exception {
        String json = """
                {
                  "primaryRoles": ["DEVELOPMENT", "PLANNING"],
                  "roles": ["BACKEND_DEVELOPER", "DATA_ANALYST"],
                  "activityStatus": "LOOKING_FOR_TEAM",
                  "grade": 3,
                  "profileImageUrl": "https://cdn.giut.com/profiles/12.png",
                  "bio": "백엔드와 AI 프로젝트에 관심이 있습니다.",
                  "searchable": true,
                  "skillTagIds": [1, 4],
                  "interestTagIds": [21, 25],
                  "experienceTagIds": [],
                  "activityHistories": [{
                    "category": "AWARD",
                    "title": "서울시 데이터 활용 공모전 우수상",
                    "organization": "서울특별시",
                    "startMonth": "2025-03",
                    "endMonth": "2025-06"
                  }]
                }
                """;

        UpdateMyProfileRequest request = objectMapper.readValue(json, UpdateMyProfileRequest.class);

        assertThat(objectMapper.valueToTree(request).has("departmentname")).isFalse();
        assertThat(objectMapper.valueToTree(request).has("departmentName")).isFalse();
        assertThat(objectMapper.valueToTree(request).has("department")).isFalse();
        assertThat(request.activityHistories()).hasSize(1);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"department", "departmentname", "departmentName"})
    void ignoresDepartmentFieldsSentByOlderClients(String fieldName) throws Exception {
        String json = """
                {
                  "%s": "컴퓨터과학부",
                  "primaryRoles": ["DEVELOPMENT"],
                  "roles": ["BACKEND_DEVELOPER"],
                  "activityStatus": "LOOKING_FOR_TEAM",
                  "grade": 3,
                  "searchable": true,
                  "skillTagIds": [],
                  "interestTagIds": [],
                  "experienceTagIds": [],
                  "activityHistories": []
                }
                """.formatted(fieldName);

        UpdateMyProfileRequest request = objectMapper.readValue(json, UpdateMyProfileRequest.class);

        assertThat(objectMapper.valueToTree(request).has(fieldName)).isFalse();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void requiresActivityHistoriesArrayForFullReplacement() throws Exception {
        String json = """
                {
                  "primaryRoles": ["DEVELOPMENT"],
                  "roles": ["BACKEND_DEVELOPER"],
                  "activityStatus": "LOOKING_FOR_TEAM",
                  "grade": 3,
                  "searchable": true,
                  "skillTagIds": [],
                  "interestTagIds": [],
                  "experienceTagIds": []
                }
                """;
        UpdateMyProfileRequest request = objectMapper.readValue(json, UpdateMyProfileRequest.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("activityHistories"));
        }
    }
}
