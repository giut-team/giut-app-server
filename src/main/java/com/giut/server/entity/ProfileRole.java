package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Entity
@Table(name = "profile_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_role", nullable = false, length = 30)
    private PrimaryRole primaryRole;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Getter
    @RequiredArgsConstructor
    public enum PrimaryRole {
        DEVELOPMENT("개발", 1),
        DESIGN("디자인", 2),
        PLANNING("기획", 3),
        MARKETING("마케팅", 4);

        private final String displayName;

        private final int displayOrder;

        public static PrimaryRole fromCode(String code) {
            return Arrays.stream(values())
                    .filter(primaryRole -> primaryRole.name().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 대표 역할이 포함되어 있습니다."));
        }
    }
}
