package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Table(
        name = "profile_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_profile_links_user_type",
                columnNames = {"user_id", "link_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 100)
    private String title;

    public static ProfileLink create(Long userId, Type type, String url, String title) {
        ProfileLink profileLink = new ProfileLink();
        profileLink.userId = userId;
        profileLink.type = type;
        profileLink.url = url;
        profileLink.title = title;
        return profileLink;
    }

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
