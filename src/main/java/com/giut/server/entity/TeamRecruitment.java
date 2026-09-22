package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "team_recruitments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_recruitments_team_role",
                columnNames = {"team_id", "role_code"}
        ),
        indexes = @Index(name = "idx_team_recruitments_team_id", columnList = "team_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRecruitment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(name = "required_count", nullable = false)
    private Short requiredCount;

    public static TeamRecruitment create(Long teamId, String roleCode, Short requiredCount) {
        TeamRecruitment recruitment = new TeamRecruitment();
        recruitment.teamId = teamId;
        recruitment.roleCode = roleCode;
        recruitment.requiredCount = requiredCount;
        return recruitment;
    }
}
