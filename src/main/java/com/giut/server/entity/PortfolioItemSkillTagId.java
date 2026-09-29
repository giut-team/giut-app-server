package com.giut.server.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioItemSkillTagId implements Serializable {

    private Long portfolioItem;
    private Long tag;
}
