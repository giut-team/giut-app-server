package com.giut.server.competition.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
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
        JsonNode node = context.readTree(parser);
        JsonNode items = node != null && node.isObject() ? node.get("competitions") : node;
        if (items == null || !items.isArray()) {
            return new AiCompetitionBatchRequest(null);
        }
        List<JsonNode> competitions = new ArrayList<>();
        items.forEach(competitions::add);
        return new AiCompetitionBatchRequest(competitions);
    }
}
