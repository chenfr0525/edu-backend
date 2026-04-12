package com.edu.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "student_knowledge_mastery")
@NoArgsConstructor
@AllArgsConstructor
public class StudentKnowledgeMastery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

     @OneToOne
    @JoinColumn(name = "student")
    private Student student;

    @OneToOne
    @JoinColumn(name = "knowledge_point_id")
    private KnowledgePoint knowledgePoint;

    @Column(name = "mastery_level")
    private Double masteryLevel = 0.0;

    private Double score;

    @Column(name = "last_practice_time")
    private LocalDateTime lastPracticeTime;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "weakness_level")
    private String weaknessLevel;

    @Column(name = "suggested_actions")
    private String suggestedActions;

    @Column(name = "last_exam_score_rate")
    private BigDecimal lastExamScoreRate;

}
