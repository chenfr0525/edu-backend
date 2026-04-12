package com.edu.domain;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.persistence.*;

@Entity
@Table(name = "class_wrong_question_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassWrongQuestionStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "class_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cwqs_class"))
    private ClassInfo classInfo;

     @ManyToOne
    @JoinColumn(name = "knowledge_point_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cwqs_knowledge_point"))
    private KnowledgePoint knowledgePoint;
    
    /**
     * 来源: HOMEWORK/EXAM
     */
    @Column(name = "source_type")
    private String sourceType;
    
    @Column(name = "source_id")
    private Long sourceId;
    
    /**
     * 错误人数
     */
    @Column(name = "error_count")
    private Integer errorCount;
    
    /**
     * 总学生数
     */
    @Column(name = "total_students")
    private Integer totalStudents;
    
    /**
     * 错误率百分比
     */
    @Column(name = "error_rate")
    private BigDecimal errorRate;
    
    /**
     * 错题排名
     */
    @Column(name = "rank_in_class")
    private Integer rankInClass;
    
    @Column(name = "stat_date")
    private LocalDate statDate;
}
