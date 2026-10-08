package com.giut.server.profile.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_recommendations",
        uniqueConstraints = @UniqueConstraint(name = "uk_profile_recommendations_pair",
                columnNames = {"recommender_user_id", "recommended_user_id"}),
        indexes = @Index(name = "idx_profile_recommendations_target", columnList = "recommended_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileRecommendation extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommender_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_recommendations_actor"))
    private User recommender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_recommendations_target"))
    private User recommendedUser;

    public static ProfileRecommendation create(User recommender, User recommendedUser) {
        ProfileRecommendation recommendation = new ProfileRecommendation();
        recommendation.recommender = recommender;
        recommendation.recommendedUser = recommendedUser;
        return recommendation;
    }
}
