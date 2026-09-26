package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.YearMonth;

@Entity
@Table(
        name = "activity_histories",
        indexes = @Index(name = "idx_activity_histories_user_period", columnList = "user_id, start_month")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_activity_histories_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String organization;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "end_month", nullable = false)
    private YearMonth endMonth;

    public static ActivityHistory create(
            User user,
            Category category,
            String title,
            String organization,
            YearMonth startMonth,
            YearMonth endMonth
    ) {
        ActivityHistory activityHistory = new ActivityHistory();
        activityHistory.user = user;
        activityHistory.update(category, title, organization, startMonth, endMonth);
        return activityHistory;
    }

    public void update(
            Category category,
            String title,
            String organization,
            YearMonth startMonth,
            YearMonth endMonth
    ) {
        this.category = category;
        this.title = title;
        this.organization = organization;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Category {
        AWARD("수상"),
        EXTERNAL_ACTIVITY("대외활동"),
        CLUB("동아리"),
        INTERNSHIP("인턴"),
        CERTIFICATION("자격증");

        private final String displayName;
    }
}
