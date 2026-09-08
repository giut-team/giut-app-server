package com.giut.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "team_application_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_application_answers_application_question",
                columnNames = {"application_id", "question_id"}
        ),
        indexes = {
                @Index(name = "idx_team_application_answers_application_id", columnList = "application_id"),
                @Index(name = "idx_team_application_answers_question_id", columnList = "question_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamApplicationAnswer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    public static TeamApplicationAnswer create(Long applicationId, Long questionId, String answer) {
        TeamApplicationAnswer applicationAnswer = new TeamApplicationAnswer();
        applicationAnswer.applicationId = applicationId;
        applicationAnswer.questionId = questionId;
        applicationAnswer.answer = answer;
        return applicationAnswer;
    }
}
