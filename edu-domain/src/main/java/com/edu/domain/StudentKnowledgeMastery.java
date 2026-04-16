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

    // 修正：使用 ManyToOne 而不是 OneToOne，因为一个学生可以有多个知识点掌握记录
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)  // 改为 student_id
    private Student student;

    @ManyToOne  // 改为 ManyToOne，一个知识点可以被多个学生掌握
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
