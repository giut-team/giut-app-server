package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "user_profile_tags") @IdClass(UserProfileTagId.class) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileTag {
    @Id @Column(name = "user_id")
    private Long userId;

    @Id @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
