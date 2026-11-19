package com.giut.server.team.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "team_invite_links", uniqueConstraints =
        @UniqueConstraint(name = "uk_team_invite_links_token_hash", columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamInviteLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static TeamInviteLink create(Long teamId, String roleCode, String tokenHash, Instant expiresAt) {
        TeamInviteLink link = new TeamInviteLink();
        link.teamId = teamId;
        link.roleCode = roleCode;
        link.tokenHash = tokenHash;
        link.expiresAt = expiresAt;
        return link;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        usedAt = now;
    }

    public void revoke(Instant now) {
        revokedAt = now;
    }
}
