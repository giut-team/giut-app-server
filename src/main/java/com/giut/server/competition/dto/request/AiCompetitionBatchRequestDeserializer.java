package com.giut.server.competition.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.List;

/**
 * The AI client sends a bare JSON array for a batch. {"competitions": [...]} is still
 * accepted for backward compatibility with earlier manual testing.
 */
public class AiCompetitionBatchRequestDeserializer extends StdDeserializer<AiCompetitionBatchRequest> {
    public AiCompetitionBatchRequestDeserializer() {
        super(AiCompetitionBatchRequest.class);
    }

    @Override
    public AiCompetitionBatchRequest deserialize(JsonParser parser, DeserializationContext context) {
        JavaType listType = context.getTypeFactory().constructCollectionType(List.class, AiCompetitionRequest.class);
        if (parser.hasToken(JsonToken.START_ARRAY)) {
            List<AiCompetitionRequest> items = context.readValue(parser, listType);
            return new AiCompetitionBatchRequest(items);
        }
        JsonNode node = context.readTree(parser);
        JsonNode competitionsNode = node.get("competitions");
        if (competitionsNode == null) {
            return new AiCompetitionBatchRequest(null);
        }
        List<AiCompetitionRequest> items = context.readTreeAsValue(competitionsNode, listType);
        return new AiCompetitionBatchRequest(items);
    }
}
