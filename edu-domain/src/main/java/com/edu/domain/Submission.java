package com.edu.domain;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "homework_id")
    private Homework homework;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String attachments;
    
    private Double score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @Column(name = "knowledge_point_scores")
    private String knowledgePointScores;

    @Column(name = "submission_late_minutes")
    private Integer submissionLateMinutes;

    @Column(name = "ai_feedback")
    private String aiFeedback;

    @PrePersist
    protected void onSubmit() {
        submittedAt = LocalDateTime.now();
        if (status == null) status = SubmissionStatus.SUBMITTED;
    }
}
