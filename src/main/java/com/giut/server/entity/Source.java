package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "sources") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Short id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20) private Type sourceType;
    @Enumerated(EnumType.STRING) @Column(name = "trust_type", nullable = false, length = 20) private TrustType trustType;
    @Enumerated(EnumType.STRING) @Column(name = "verification_policy", nullable = false, length = 30) private VerificationPolicy verificationPolicy;
    @Column(name = "base_url", columnDefinition = "text") private String baseUrl;
    @Column(name = "is_active", nullable = false) private boolean active = true;

    public enum Type { RSS, API, SCRAPING, USER_SUBMISSION }
    public enum TrustType { OFFICIAL, THIRD_PARTY, USER_GENERATED }
    public enum VerificationPolicy { TRUST_SOURCE, AUTO_IF_COMPLETE, MANUAL_REQUIRED }
}
