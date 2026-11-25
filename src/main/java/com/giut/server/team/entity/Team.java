package com.giut.server.team.entity;

import com.giut.server.competition.entity.Competition;
import com.giut.server.global.entity.BaseTimeEntity;
import com.giut.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "teams", indexes = @Index(
        name = "idx_teams_competition_status", columnList = "competition_id, status"
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_teams_leader_user"))
    private User leader;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING) @Column(name = "activity_mode", nullable = false, length = 10)
    private ActivityMode activityMode;

    @Column(name = "max_member_count")
    private Short maxMemberCount;

    @Column(name = "weekly_meeting_count", nullable = false)
    private Short weeklyMeetingCount;

    @Enumerated(EnumType.STRING) @Column(name = "meeting_place", length = 30)
    private MeetingPlace meetingPlace;

    @ElementCollection
    @CollectionTable(name = "team_collaboration_tools",
            joinColumns = @JoinColumn(name = "team_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_team_collaboration_tools_team"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tool_code", nullable = false, length = 30)
    private Set<CollaborationTool> collaborationTools = new HashSet<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;

    public enum ActivityMode { ONLINE, OFFLINE, HYBRID }
    public enum CollaborationTool { DISCORD, ZOOM, GOOGLE_MEET, NOTION, SLACK }
    public enum MeetingPlace { CAMPUS, SEOUL, METROPOLITAN_AREA, ANYWHERE }
    public enum Status { RECRUITING, CLOSED, ARCHIVED }

    public void closeRecruitment() {
        this.status = Status.CLOSED;
    }

    public void updateInfo(
            String name,
            String description,
            ActivityMode activityMode,
            Short maxMemberCount,
            Short weeklyMeetingCount,
            MeetingPlace meetingPlace
    ) {
        this.name = name;
        this.description = description;
        this.activityMode = activityMode;
        this.maxMemberCount = maxMemberCount;
        this.weeklyMeetingCount = weeklyMeetingCount;
        this.meetingPlace = meetingPlace;
    }

    public void archive() {
        this.status = Status.ARCHIVED;
    }

    public void replaceCollaborationTools(Set<CollaborationTool> tools) {
        this.collaborationTools.clear();
        this.collaborationTools.addAll(tools);
    }

    public List<CollaborationTool> collaborationToolCodes() {
        return collaborationTools.stream().sorted().toList();
    }

    public static Team create(
            Competition competition,
            User leader,
            String name,
            String description,
            ActivityMode activityMode,
            Short maxMemberCount,
            Short weeklyMeetingCount,
            MeetingPlace meetingPlace
    ) {
        Team team = new Team();
        team.competition = competition;
        team.leader = leader;
        team.name = name;
        team.description = description;
        team.activityMode = activityMode;
        team.maxMemberCount = maxMemberCount;
        team.weeklyMeetingCount = weeklyMeetingCount;
        team.meetingPlace = meetingPlace;
        team.status = Status.RECRUITING;
        return team;
    }
}
