package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "profile_tags", uniqueConstraints = @UniqueConstraint(name = "uk_profile_tags_type_name", columnNames = {"tag_type", "normalized_name"})) @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "tag_type", nullable = false, length = 20) private TagType tagType;
    @Column(nullable = false, length = 80) private String name;
    @Column(name = "normalized_name", nullable = false, length = 80) private String normalizedName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public enum TagType { SKILL, INTEREST, EXPERIENCE }
}
