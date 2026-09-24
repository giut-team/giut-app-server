package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
@Table(name = "user_profile_roles")
@IdClass(UserProfileRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_profile_roles_profile"))
    private UserProfile profile;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private ProfileRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserProfileRole create(UserProfile profile, ProfileRole role) {
        UserProfileRole userProfileRole = new UserProfileRole();
        userProfileRole.profile = profile;
        userProfileRole.role = role;
        return userProfileRole;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
