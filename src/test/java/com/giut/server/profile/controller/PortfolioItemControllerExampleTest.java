package com.giut.server.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giut.server.profile.dto.request.UpsertPortfolioItemRequest;
import com.giut.server.profile.controller.PortfolioItemController;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioItemControllerExampleTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createAndUpdateExamplesRequireRoleCodes() throws Exception {
        Operation create = PortfolioItemController.class
                .getMethod("createPortfolioItem", Authentication.class, UpsertPortfolioItemRequest.class)
                .getAnnotation(Operation.class);
        Operation update = PortfolioItemController.class
                .getMethod("updatePortfolioItem", Authentication.class, Long.class, UpsertPortfolioItemRequest.class)
                .getAnnotation(Operation.class);

        for (Operation operation : new Operation[]{create, update}) {
            String example = operation.requestBody().content()[0].examples()[0].value();
            UpsertPortfolioItemRequest request = objectMapper.readValue(example, UpsertPortfolioItemRequest.class);

            assertThat(request.roles()).containsExactly("BACKEND_DEVELOPER", "DATA_ANALYST");
            assertThat(objectMapper.readTree(example).has("myRole")).isFalse();
        }
    }
}
