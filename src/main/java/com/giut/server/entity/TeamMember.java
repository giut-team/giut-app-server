package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "team_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_members_team_user",
                columnNames = {"team_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_team_members_team_id", columnList = "team_id"),
                @Index(name = "idx_team_members_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public enum Role {
        LEADER,
        MEMBER
    }

    public enum Status {
        ACTIVE,
        LEFT,
        KICKED
    }

    public static TeamMember createLeader(Long teamId, Long userId) {
        return create(teamId, userId, Role.LEADER);
    }

    public static TeamMember createMember(Long teamId, Long userId) {
        return create(teamId, userId, Role.MEMBER);
    }

    private static TeamMember create(Long teamId, Long userId, Role role) {
        TeamMember teamMember = new TeamMember();
        teamMember.teamId = teamId;
        teamMember.userId = userId;
        teamMember.role = role;
        teamMember.status = Status.ACTIVE;
        teamMember.joinedAt = Instant.now();
        return teamMember;
    }

    public void leave() {
        this.status = Status.LEFT;
    }

    public void kick() {
        this.status = Status.KICKED;
    }
}
