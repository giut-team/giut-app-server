package com.giut.server.competition.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import com.giut.server.competition.entity.Competition;

/** Translates AI category codes without changing the existing public category codes. */
public class AiCompetitionCategoryDeserializer extends StdDeserializer<Competition.Category> {
    public AiCompetitionCategoryDeserializer() {
        super(Competition.Category.class);
    }

    @Override
    public Competition.Category deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(tools.jackson.core.JsonToken.VALUE_STRING)) {
            return (Competition.Category) context.handleUnexpectedToken(Competition.Category.class, parser);
        }
        String value = parser.getText();
        try {
            return resolveCategory(value);
        } catch (IllegalArgumentException ex) {
            return (Competition.Category) context.handleWeirdStringValue(Competition.Category.class, value,
                    "지원하지 않는 카테고리입니다.");
        }
    }

    public static Competition.Category resolveCategory(String value) {
        return switch (value) {
            case "IDEA" -> Competition.Category.PLANNING_IDEA;
            case "MARKETING" -> Competition.Category.ADVERTISING_MARKETING;
            case "IT_DEVELOP" -> Competition.Category.WEB_MOBILE_IT;
            case "DESIGN" -> Competition.Category.DESIGN_CHARACTER_WEBTOON;
            case "PHOTO_CONTENTS" -> Competition.Category.VIDEO_UCC_PHOTO;
            case "LITERATURE" -> Competition.Category.LITERATURE_WRITING_SCENARIO;
            case "RESEARCH" -> Competition.Category.PAPER_REPORT;
            case "CULTURE_ARTS" -> Competition.Category.ENTERTAINMENT_ART_MUSIC;
            default -> Competition.Category.valueOf(value);
        };
    }
}
