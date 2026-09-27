package com.giut.server.entity;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserProfileTagId implements Serializable {
    private Long profile;

    private Long tag;
}
