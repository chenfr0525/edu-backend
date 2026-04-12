package com.edu.domain;

import javax.persistence.*;

import org.springframework.data.annotation.CreatedDate;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "score_prediction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScorePrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_student"))
    private Student student;

      @ManyToOne
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sp_course"))
    private Course course;
    
    /**
     * 考试类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type")
    private ExamStatus examType=ExamStatus.MOCK;
    
    /**
     * 预测分数
     */
    @Column(name = "predicted_score")
    private BigDecimal predictedScore;
    
    /**
     * 置信区间下限
     */
    @Column(name = "confidence_lower")
    private BigDecimal confidenceLower;
    
    /**
     * 置信区间上限
     */
    @Column(name = "confidence_upper")
    private BigDecimal confidenceUpper;
    
    /**
     * 趋势: IMPROVING/STABLE/DECLINING
     */
    @Column(name = "trend")
    private String trend;
    
    /**
     * 影响因素(JSON)
     */
    @Column(name = "factors", columnDefinition = "JSON")
    private String factors;
    
   @CreatedDate
    @Column(name = "prediction_date", nullable = false, updatable = false)
    private LocalDateTime predictionDate;
    
    /**
     * 实际成绩(用于验证)
     */
     @Column(name = "actual_score", precision = 5, scale = 2)
    private BigDecimal actualScore;
}
