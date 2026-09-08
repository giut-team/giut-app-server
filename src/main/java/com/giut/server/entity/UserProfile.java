package com.giut.server.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;

@Entity @Table(name = "user_profiles") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseTimeEntity {
    @Id @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_profiles_user"))
    private User user;

    @Enumerated(EnumType.STRING) @Column(name = "department_type", nullable = false, length = 50)
    private DepartmentType department;

    @Enumerated(EnumType.STRING) @Column(name = "activity_status", nullable = false, length = 30)
    private ActivityStatus activityStatus;

    private Short grade;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Gender gender;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "is_searchable", nullable = false)
    private boolean searchable = true;

    /**
     * 검색 조건으로 사용하지 않는 대표 역할은 JSON 배열로 보관한다.
     * 예: ["DEVELOPMENT", "PLANNING"]
     */
    @Column(name = "primary_roles", columnDefinition = "json", nullable = false)
    private String primaryRolesJson;

    /**
     * 프로필 전체 수정 시 함께 교체되는 외부 링크 목록이다.
     */
    @Column(name = "external_links", columnDefinition = "json", nullable = false)
    private String externalLinksJson;

    public static UserProfile create(
            User user,
            DepartmentType department,
            ActivityStatus activityStatus,
            Short grade,
            Gender gender,
            String profileImageUrl,
            String bio,
            boolean searchable,
            String primaryRolesJson,
            String externalLinksJson
    ) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        profile.update(
                department,
                activityStatus,
                grade,
                gender,
                profileImageUrl,
                bio,
                searchable,
                primaryRolesJson,
                externalLinksJson
        );
        return profile;
    }

    public void update(
            DepartmentType department,
            ActivityStatus activityStatus,
            Short grade,
            Gender gender,
            String profileImageUrl,
            String bio,
            boolean searchable,
            String primaryRolesJson,
            String externalLinksJson
    ) {
        this.department = department;
        this.activityStatus = activityStatus;
        this.grade = grade;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.searchable = searchable;
        this.primaryRolesJson = primaryRolesJson;
        this.externalLinksJson = externalLinksJson;
    }

    @Getter
    @RequiredArgsConstructor
    public enum DepartmentType {
        PUBLIC_ADMINISTRATION("행정학과"),
        INTERNATIONAL_RELATIONS("국제관계학과"),
        ECONOMICS("경제학부"),
        SOCIAL_WELFARE("사회복지학과"),
        TAXATION("세무학과"),
        BUSINESS_ADMINISTRATION("경영학부"),
        ELECTRICAL_AND_COMPUTER_ENGINEERING("전자전기컴퓨터공학부"),
        CHEMICAL_ENGINEERING("화학공학과"),
        MECHANICAL_AND_INFORMATION_ENGINEERING("기계정보공학과"),
        MATERIALS_SCIENCE_AND_ENGINEERING("신소재공학과"),
        CIVIL_ENGINEERING("토목공학과"),
        ENGLISH_LANGUAGE_AND_LITERATURE("영어영문학과"),
        KOREAN_LANGUAGE_AND_LITERATURE("국어국문학과"),
        KOREAN_HISTORY("국사학과"),
        PHILOSOPHY("철학과"),
        CHINESE_LANGUAGE_AND_CULTURE("중국어문화학과"),
        MATHEMATICS("수학과"),
        STATISTICS("통계학과"),
        PHYSICS("물리학과"),
        LIFE_SCIENCE("생명과학과"),
        ENVIRONMENTAL_HORTICULTURE("환경원예학과"),
        CONVERGENCE_APPLIED_CHEMISTRY("융합응용화학과"),
        URBAN_ADMINISTRATION("도시행정학과"),
        URBAN_SOCIOLOGY("도시사회학과"),
        ARCHITECTURE("건축학부"),
        URBAN_ENGINEERING("도시공학과"),
        TRANSPORTATION_ENGINEERING("교통공학과"),
        LANDSCAPE_ARCHITECTURE("조경학과"),
        ENVIRONMENTAL_ENGINEERING("환경공학부"),
        GEOINFORMATICS_ENGINEERING("공간정보공학과"),
        DESIGN("디자인학과"),
        SCULPTURE("조각학과"),
        MUSIC("음악학과"),
        SPORTS_SCIENCE("스포츠과학과"),
        LIBERAL_STUDIES("자유전공학부"),
        CONVERGENCE_STUDIES("융합전공학부"),
        COMPUTER_SCIENCE("컴퓨터과학부"),
        ARTIFICIAL_INTELLIGENCE("인공지능학과"),
        ADVANCED_CONVERGENCE("첨단융합학부");

        private final String displayName;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static DepartmentType from(String value) {
            return Arrays.stream(values())
                    .filter(department -> department.displayName.equals(value) || department.name().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 학과입니다: " + value));
        }
    }

    public enum Gender { MALE, FEMALE }

    @Getter
    @RequiredArgsConstructor
    public enum ActivityStatus {
        LOOKING_FOR_TEAM("팀 찾는 중"),
        OPEN_TO_OFFERS("프로필 쓰는 중"),
        RESTING("지금은 쉬는 중");

        private final String displayName;
    }

}
