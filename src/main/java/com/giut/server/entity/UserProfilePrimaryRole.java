package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_profile_primary_roles")
@IdClass(UserProfilePrimaryRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfilePrimaryRole {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_role_id", nullable = false)
    private ProfileRoleCategory primaryRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserProfilePrimaryRole create(Long userId, ProfileRoleCategory primaryRole) {
        UserProfilePrimaryRole userProfilePrimaryRole = new UserProfilePrimaryRole();
        userProfilePrimaryRole.userId = userId;
        userProfilePrimaryRole.primaryRole = primaryRole;
        return userProfilePrimaryRole;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
