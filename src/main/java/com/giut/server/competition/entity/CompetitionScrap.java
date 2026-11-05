package com.giut.server.competition.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
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
// user 락(findActiveUserForScrapUpdate)이 같은 사용자의 동시 요청을 직렬화하지만,
// 그 락만으로는 모든 경쟁 상태를 막는다고 가정하지 않고 DB unique 제약을 최종 방어선으로 둔다.
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
