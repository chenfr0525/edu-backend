package com.edu.domain;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "homework")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Homework {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "knowledge_point_id")
    private KnowledgePoint knowledgePoint;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "question_count")
    private Integer questionCount;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private HomeworkStatus status = HomeworkStatus.ONGING;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
