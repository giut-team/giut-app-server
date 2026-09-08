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
@Table(name = "profile_role_skill_tags")
@IdClass(ProfileRoleSkillTagId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileRoleSkillTag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private ProfileRole role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private ProfileTag tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ProfileRoleSkillTag create(ProfileRole role, ProfileTag tag) {
        ProfileRoleSkillTag profileRoleSkillTag = new ProfileRoleSkillTag();
        profileRoleSkillTag.role = role;
        profileRoleSkillTag.tag = tag;
        return profileRoleSkillTag;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
