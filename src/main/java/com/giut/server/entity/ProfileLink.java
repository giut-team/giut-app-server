package com.giut.server.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * user_profiles.external_links JSON 배열에 저장하는 값 객체다.
 * 링크 단건을 따로 수정하는 API가 없으므로 독립 테이블과 식별자는 두지 않는다.
 */
public record ProfileLink(Type type, String url, String title) {

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        GITHUB("GitHub"),
        NOTION("Notion"),
        PORTFOLIO_PDF("포트폴리오 PDF"),
        WEBSITE("웹사이트");

        private final String displayName;
    }
}
