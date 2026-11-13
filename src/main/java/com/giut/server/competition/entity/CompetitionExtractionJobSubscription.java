package com.giut.server.competition.entity;

import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * 추출 작업을 조회할 수 있는 회원. 같은 URL의 작업은 회원 수와 무관하게 하나만 실행하므로,
 * 작업을 처음 접수한 회원과 나중에 같은 URL로 합류한 회원이 모두 이 행으로 자기 작업을 조회한다.
 *
 * <p>작업({@link CompetitionExtractionJob})이 삭제되면 DB가 이 행도 함께 지운다({@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "competition_extraction_job_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_extraction_subscriptions_job_user", columnNames = {"job_id", "user_id"}),
        indexes = @Index(name = "idx_extraction_subscriptions_user", columnList = "user_id, job_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetitionExtractionJobSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_extraction_subscriptions_job"))
    private CompetitionExtractionJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_extraction_subscriptions_user"))
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CompetitionExtractionJobSubscription of(CompetitionExtractionJob job, User user, Instant now) {
        CompetitionExtractionJobSubscription subscription = new CompetitionExtractionJobSubscription();
        subscription.job = job;
        subscription.user = user;
        subscription.createdAt = now;
        return subscription;
    }
}
