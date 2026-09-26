package com.giut.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "portfolio_item_skill_tags")
@IdClass(PortfolioItemSkillTagId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItemSkillTag {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "portfolio_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_portfolio_item_skill_tags_item"))
    private PortfolioItem portfolioItem;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "tag_id", nullable = false, foreignKey = @ForeignKey(name = "fk_portfolio_item_skill_tags_tag"))
    private ProfileTag tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PortfolioItemSkillTag create(PortfolioItem portfolioItem, ProfileTag tag) {
        PortfolioItemSkillTag portfolioItemSkillTag = new PortfolioItemSkillTag();
        portfolioItemSkillTag.portfolioItem = portfolioItem;
        portfolioItemSkillTag.tag = tag;
        return portfolioItemSkillTag;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
