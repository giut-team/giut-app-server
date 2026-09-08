package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String caption;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static PortfolioItem create(
            Long userId,
            String imageUrl,
            String title,
            String caption,
            String content,
            int displayOrder
    ) {
        PortfolioItem portfolioItem = new PortfolioItem();
        portfolioItem.userId = userId;
        portfolioItem.imageUrl = imageUrl;
        portfolioItem.title = title;
        portfolioItem.caption = caption;
        portfolioItem.content = content;
        portfolioItem.displayOrder = displayOrder;
        return portfolioItem;
    }

    public void update(String imageUrl, String title, String caption, String content) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.caption = caption;
        this.content = content;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
