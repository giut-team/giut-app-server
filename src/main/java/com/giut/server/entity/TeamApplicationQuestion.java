package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "team_application_questions",
        indexes = @Index(name = "idx_team_application_questions_team_order", columnList = "team_id, display_order")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamApplicationQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(nullable = false, length = 300)
    private String question;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    public enum Status {
        ACTIVE,
        DELETED
    }

    public static TeamApplicationQuestion create(Long teamId, String question, boolean required, int displayOrder) {
        TeamApplicationQuestion applicationQuestion = new TeamApplicationQuestion();
        applicationQuestion.teamId = teamId;
        applicationQuestion.question = question;
        applicationQuestion.required = required;
        applicationQuestion.displayOrder = displayOrder;
        applicationQuestion.status = Status.ACTIVE;
        return applicationQuestion;
    }
}
