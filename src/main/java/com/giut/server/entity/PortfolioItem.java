package com.giut.server.entity;

import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "portfolio_items",
        indexes = @Index(name = "idx_portfolio_items_user_order", columnList = "user_id, display_order")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_portfolio_items_user"))
    private User user;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String caption;

    @Column(name = "project_start_date")
    private LocalDate projectStartDate;

    @Column(name = "project_end_date")
    private LocalDate projectEndDate;

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(name = "markdown_content", nullable = false, columnDefinition = "text")
    private String markdownContent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "showcase_order")
    private Integer showcaseOrder;

    @Column(name = "is_representative", nullable = false)
    private boolean representative;

    public static PortfolioItem create(
            User user,
            String imageUrl,
            String title,
            String caption,
            LocalDate projectStartDate,
            LocalDate projectEndDate,
            Integer teamSize,
            String markdownContent,
            int displayOrder
    ) {
        PortfolioItem portfolioItem = new PortfolioItem();
        portfolioItem.user = user;
        portfolioItem.imageUrl = imageUrl;
        portfolioItem.title = title;
        portfolioItem.caption = caption;
        portfolioItem.projectStartDate = projectStartDate;
        portfolioItem.projectEndDate = projectEndDate;
        portfolioItem.teamSize = teamSize;
        portfolioItem.markdownContent = markdownContent;
        portfolioItem.displayOrder = displayOrder;
        return portfolioItem;
    }

    public void update(
            String imageUrl,
            String title,
            String caption,
            LocalDate projectStartDate,
            LocalDate projectEndDate,
            Integer teamSize,
            String markdownContent
    ) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.caption = caption;
        this.projectStartDate = projectStartDate;
        this.projectEndDate = projectEndDate;
        this.teamSize = teamSize;
        this.markdownContent = markdownContent;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void changeShowcaseOrder(Integer showcaseOrder) {
        this.showcaseOrder = showcaseOrder;
        if (showcaseOrder == null) {
            this.representative = false;
        }
    }

    public void makeRepresentative() {
        if (showcaseOrder == null) {
            throw new IllegalStateException("공개 프로필에 노출 중인 포트폴리오만 대표로 지정할 수 있습니다.");
        }
        this.representative = true;
    }

    public void clearRepresentative() {
        this.representative = false;
    }
}
