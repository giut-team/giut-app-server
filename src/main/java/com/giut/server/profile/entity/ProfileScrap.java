package com.giut.server.profile.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_scraps", uniqueConstraints = @UniqueConstraint(
        name = "uk_profile_scraps_user_profile", columnNames = {"user_id", "profile_user_id"}),
        indexes = @Index(name = "idx_profile_scraps_profile", columnList = "profile_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileScrap extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_profile_scraps_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_profile_scraps_profile"))
    private UserProfile profile;

    public static ProfileScrap create(User user, UserProfile profile) {
        ProfileScrap scrap = new ProfileScrap();
        scrap.user = user;
        scrap.profile = profile;
        return scrap;
    }
}
