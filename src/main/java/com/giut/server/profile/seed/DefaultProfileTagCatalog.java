package com.giut.server.profile.seed;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultProfileTagCatalog {

    // Keep the existing interest/experience defaults from db/seed/profile_tags.sql.
    public List<String> interests() {
        return List.of("IT/과학", "데이터", "창업", "환경/ESG", "문화/예술");
    }

    public List<String> experiences() {
        return List.of("공모전", "해커톤", "팀 프로젝트", "창업", "대외활동", "인턴십", "동아리", "수상");
    }
}
