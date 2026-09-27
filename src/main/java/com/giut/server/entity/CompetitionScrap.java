package com.giut.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "competition_scraps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_competition_scraps_user_competition",
                columnNames = {"user_id", "competition_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionScrap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_competition_scraps_user"))
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "competition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_competition_scraps_competition"))
    private Competition competition;

    public static CompetitionScrap create(User user, Competition competition) {
        CompetitionScrap scrap = new CompetitionScrap();
        scrap.user = user;
        scrap.competition = competition;
        return scrap;
    }
}
