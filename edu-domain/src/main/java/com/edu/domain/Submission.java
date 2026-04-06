package com.edu.domain;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
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
    private User student;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String files; // JSON or comma-separated list of file paths/names

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    private Double score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @PrePersist
    protected void onSubmit() {
        submittedAt = LocalDateTime.now();
        if (status == null) status = SubmissionStatus.SUBMITTED;
    }
}
