package com.giut.server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "teams") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id", nullable = false) private Competition competition;

    @Column(name = "leader_user_id", nullable = false) private Long leaderUserId;

    @Column(nullable = false, length = 100) private String name;

    @Column(columnDefinition = "text") private String description;

    @Enumerated(EnumType.STRING) @Column(name = "activity_mode", nullable = false, length = 10) private ActivityMode activityMode;

    @Column(name = "max_member_count") private Short maxMemberCount;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;

    public enum ActivityMode { ONLINE, OFFLINE, HYBRID }
    public enum Status { RECRUITING, CLOSED, ARCHIVED }
}
