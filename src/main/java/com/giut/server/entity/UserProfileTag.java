package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "user_profile_tags") @IdClass(UserProfileTagId.class) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileTag {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_profile_tags_profile"))
    private UserProfile profile;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private ProfileTag tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserProfileTag create(UserProfile profile, ProfileTag tag) {
        UserProfileTag userProfileTag = new UserProfileTag();
        userProfileTag.profile = profile;
        userProfileTag.tag = tag;
        return userProfileTag;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
