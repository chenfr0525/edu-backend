package com.edu.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "knowledge_point_score_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePointScoreDetail {
   @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_kpsd_student"))
    private Student student;

    @ManyToOne
    @JoinColumn(name = "knowledge_point_id", nullable = false, foreignKey = @ForeignKey(name = "fk_kpsd_knowledge_point"))
    private KnowledgePoint knowledgePoint;
    
    /**
     * 来源: HOMEWORK/EXAM
     */
    @Column(name = "source_type")
    private String sourceType;
    
    /**
     * 作业ID或考试ID
     */
    @Column(name = "source_id")
    private Long sourceId;
    
    /**
     * 该知识点得分率(0-100)
     */
    @Column(name = "score_rate")
    private BigDecimal scoreRate;
    
    /**
     * 该知识点满分
     */
    @Column(name = "max_score")
    private BigDecimal maxScore;
    
    /**
     * 实际得分
     */
    @Column(name = "actual_score")
    private BigDecimal actualScore;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;

     @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
