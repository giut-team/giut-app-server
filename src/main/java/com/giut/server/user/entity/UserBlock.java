package com.giut.server.user.entity;

import com.giut.server.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_blocks", check = @CheckConstraint(
        name = "chk_user_blocks_distinct", constraint = "blocker_user_id <> blocked_user_id"),
        uniqueConstraints = @UniqueConstraint(
        name = "uk_user_blocks_pair", columnNames = {"blocker_user_id", "blocked_user_id"}),
        indexes = {
                @Index(name = "idx_user_blocks_target", columnList = "blocked_user_id, blocker_user_id"),
                @Index(name = "idx_user_blocks_owner_created", columnList = "blocker_user_id, created_at, id")
        })
public class UserBlock extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_owner"))
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_target"))
    private User blockedUser;

    public static UserBlock create(User blocker, User blockedUser) {
        if (blocker.getId().equals(blockedUser.getId())) {
            throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다.");
        }
        UserBlock block = new UserBlock();
        block.blocker = blocker;
        block.blockedUser = blockedUser;
        return block;
    }
}
