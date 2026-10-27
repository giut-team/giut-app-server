package com.giut.server.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseConfigTest {

    private final ApiErrorResponseConfig config = new ApiErrorResponseConfig();

    @Test
    void withdrawalControllerExplicitlyDeclaresIts204SuccessForSwagger() throws Exception {
        var response = com.giut.server.user.controller.UserWithdrawalController.class
                .getMethod("withdraw", org.springframework.security.core.Authentication.class,
                        jakarta.servlet.http.HttpServletResponse.class)
                .getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.responseCode()).isEqualTo("204");
        assertThat(response.content()).hasSize(1);
        assertThat(response.content()[0].mediaType()).isEmpty();
    }

    @Test
    void withdrawalDocumentsLeaderConflictAndProtectedAccess() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("204", new ApiResponse().description("탈퇴 완료")));
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/api/users/me", new PathItem().delete(operation)));
        config.apiErrorResponseCustomizer().customise(api);
        assertThat(operation.getResponses()).containsKeys("204", "401", "403", "409");
        assertThat(operation.getResponses().get("204").getContent()).isNull();
    }

    @Test
    void administratorCreationDocumentsAuthenticationAuthorizationAndConflict() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("생성 성공")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/admin/auth/signup", new PathItem().post(operation)));

        config.apiErrorResponseCustomizer().customise(openApi);

        assertThat(operation.getResponses()).containsKeys("200", "401", "403", "409", "500");
    }

    @Test
    void administratorLoginRemainsPublicInErrorDocumentation() {
        Operation operation = new Operation().responses(new ApiResponses());
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/admin/auth/login", new PathItem().post(operation)));

        config.apiErrorResponseCustomizer().customise(openApi);

        assertThat(operation.getResponses()).doesNotContainKeys("403", "409");
    }

    @Test
    void documentsProtectedResourceErrorsWithResultDto() {
        Operation operation = new Operation()
                .addParametersItem(new Parameter().name("chatRoomId").in("path"))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("조회 성공")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/chat-rooms/{chatRoomId}", new PathItem().get(operation)));

        config.apiErrorResponseCustomizer().customise(openApi);

        assertThat(operation.getResponses().keySet()).contains("200", "400", "401", "403", "404", "500");
        assertThat(operation.getResponses().get("403").getContent().get("application/json")
                .getSchema().get$ref()).isEqualTo("#/components/schemas/ResultDto");
        assertThat(openApi.getComponents().getSchemas()).containsKey("ResultDto");
        assertThat(operation.getResponses().get("200").getDescription()).isEqualTo("조회 성공");
    }

    @Test
    void doesNotDocumentAuthenticationForPublicCallback() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("502", new ApiResponse().description("Apple 서버 오류")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/oauth/apple/callback", new PathItem().post(operation)));

        config.apiErrorResponseCustomizer().customise(openApi);

        assertThat(operation.getResponses()).doesNotContainKey("401");
        assertThat(operation.getResponses().get("502").getDescription()).isEqualTo("Apple 서버 오류");
        assertThat(operation.getResponses().get("502").getContent().get("application/json")
                .getSchema().get$ref()).isEqualTo("#/components/schemas/ResultDto");
    }
}
