package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_item_roles")
@IdClass(PortfolioItemRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItemRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portfolio_item_roles_item"))
    private PortfolioItem portfolioItem;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portfolio_item_roles_role"))
    private ProfileRole role;

    @Column(name = "selection_order", nullable = false)
    private int selectionOrder;

    public static PortfolioItemRole create(PortfolioItem portfolioItem, ProfileRole role, int selectionOrder) {
        PortfolioItemRole link = new PortfolioItemRole();
        link.portfolioItem = portfolioItem;
        link.role = role;
        link.selectionOrder = selectionOrder;
        return link;
    }
}
