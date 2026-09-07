package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "user_profiles") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseTimeEntity {
    @Id @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    private Short grade;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Gender gender;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "is_searchable", nullable = false)
    private boolean searchable = true;

    public static UserProfile create(
            Long userId,
            Department department,
            Short grade,
            Gender gender,
            String profileImageUrl,
            String bio,
            boolean searchable
    ) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.update(department, grade, gender, profileImageUrl, bio, searchable);
        return profile;
    }

    public void update(
            Department department,
            Short grade,
            Gender gender,
            String profileImageUrl,
            String bio,
            boolean searchable
    ) {
        this.department = department;
        this.grade = grade;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.searchable = searchable;
    }

    public enum Gender { MALE, FEMALE, OTHER, UNSPECIFIED }
}
