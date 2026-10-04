package com.giut.server.profile.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioItemRoleId implements Serializable {

    private Long portfolioItem;
    private Long role;
}
