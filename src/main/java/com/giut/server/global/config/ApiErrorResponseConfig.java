package com.giut.server.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ApiErrorResponseConfig {

    private static final Map<String, String> ERROR_DESCRIPTIONS = Map.ofEntries(
            Map.entry("400", "요청 값 또는 형식 오류"),
            Map.entry("401", "인증 실패 또는 토큰 누락"),
            Map.entry("403", "접근 권한 없음"),
            Map.entry("404", "요청한 리소스를 찾을 수 없음"),
            Map.entry("405", "지원하지 않는 HTTP 메서드"),
            Map.entry("406", "지원하지 않는 응답 형식"),
            Map.entry("409", "현재 리소스 상태와 충돌"),
            Map.entry("415", "지원하지 않는 Content-Type"),
            Map.entry("500", "서버 내부 오류"),
            Map.entry("502", "외부 인증 서버 통신 실패")
    );

    @Bean
    public OpenApiCustomizer apiErrorResponseCustomizer() {
        return openApi -> {
            registerResultDto(openApi);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }
                if (hasInputs(operation)) {
                    addError(responses, "400");
                }
                if (!isPublic(path)) {
                    addError(responses, "401");
                }
                if (!isPublic(path) && (path.startsWith("/api/admin/")
                        || path.startsWith("/api/chat-rooms/") || path.startsWith("/api/teams/"))) {
                    addError(responses, "403");
                }
                if (hasResourceId(path)) {
                    addError(responses, "404");
                }
                if ((method == PathItem.HttpMethod.POST && path.contains("/applications"))
                        || path.endsWith("/auth/signup")
                        || path.endsWith("/university-email/send")
                        || path.endsWith("/university-email/verify")) {
                    addError(responses, "409");
                }
                addError(responses, "500");

                responses.forEach((code, response) -> {
                    if (ERROR_DESCRIPTIONS.containsKey(code)) {
                        if (response.getDescription() == null || response.getDescription().isBlank()) {
                            response.setDescription(ERROR_DESCRIPTIONS.get(code));
                        }
                        response.setContent(errorContent(code));
                    }
                });
            }));
        };
    }

    private boolean hasInputs(Operation operation) {
        return operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    }

    private boolean isPublic(String path) {
        return path.startsWith("/api/oauth/")
                || path.equals("/api/admin/auth/signup")
                || path.equals("/api/admin/auth/login")
                || path.startsWith("/api/profile/shares/");
    }

    private boolean hasResourceId(String path) {
        return path.contains("{userId}") || path.contains("{teamId}")
                || path.contains("{chatRoomId}") || path.contains("{competitionId}")
                || path.contains("{portfolioItemId}") || path.contains("{applicationId}")
                || path.contains("{messageId}");
    }

    private void addError(ApiResponses responses, String code) {
        responses.putIfAbsent(code, new ApiResponse().description(ERROR_DESCRIPTIONS.get(code)));
    }

    private Content errorContent(String code) {
        return new Content().addMediaType("application/json", new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ResultDto"))
                .example(Map.of("success", false, "message", ERROR_DESCRIPTIONS.get(code),
                        "code", Integer.parseInt(code))));
    }

    private void registerResultDto(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        if (openApi.getComponents().getSchemas() == null
                || !openApi.getComponents().getSchemas().containsKey("ResultDto")) {
            openApi.getComponents().addSchemas("ResultDto", new ObjectSchema()
                    .addProperty("success", new BooleanSchema().example(false))
                    .addProperty("message", new StringSchema().example("요청 처리에 실패했습니다."))
                    .addProperty("code", new IntegerSchema().example(400)));
        }
    }
}
