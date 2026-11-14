package com.giut.server.competition.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** AI 원문과 자동 제출 결과를 함께 보관한다. 도입 전의 AI 원문만 있는 JSON도 읽는다. */
record CompetitionExtractionStoredResult(
        AiCompetitionExtractClient.Extraction extraction,
        Long submittedCompetitionId
) {
    static CompetitionExtractionStoredResult read(ObjectMapper mapper, String json) {
        var tree = (ObjectNode) mapper.readTree(json);
        var id = tree.remove("submittedCompetitionId");
        return new CompetitionExtractionStoredResult(mapper.treeToValue(tree, AiCompetitionExtractClient.Extraction.class),
                id == null || id.isNull() ? null : id.asLong());
    }

    String write(ObjectMapper mapper) {
        ObjectNode tree = mapper.valueToTree(extraction);
        if (submittedCompetitionId != null) {
            tree.put("submittedCompetitionId", submittedCompetitionId);
        }
        return mapper.writeValueAsString(tree);
    }
}
