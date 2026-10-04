package com.giut.server.team.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "team_scraps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_scraps_user_team",
                columnNames = {"user_id", "team_id"}
        ),
        indexes = @Index(name = "idx_team_scraps_team", columnList = "team_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamScrap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_scraps_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_scraps_team"))
    private Team team;

    public static TeamScrap create(User user, Team team) {
        TeamScrap scrap = new TeamScrap();
        scrap.user = user;
        scrap.team = team;
        return scrap;
    }
}
