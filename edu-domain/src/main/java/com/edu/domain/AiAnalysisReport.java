package com.edu.domain;

import java.time.LocalDateTime;

import javax.persistence.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "ai_analysis_report")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

     /**
     * 目标类型: STUDENT/CLASS/COURSE
     */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /**
     * 目标ID(学生ID/班级ID/课程ID)
     */
    @Column(name = "target_id")
    private Long targetId;
    
   @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;
    
    /**
     * 报告类型: HOMEWORK/EXAM/ACTIVITY/KNOWLEDGE/COMPREHENSIVE
     */
    @Column(name = "report_type")
    private String reportType;
    
    /**
     * AI分析数据(JSON格式)
     */
    @Column(name = "analysis_data")
    private String analysisData;
    
    /**
     * 可视化图表配置
     */
    @Column(name = "charts_config")
    private String chartsConfig;
    
    /**
     * AI生成的个性化建议
     */
    @Column(name = "suggestions")
    private String suggestions;
    
    /**
     * 分析摘要
     */
    @Column(name = "summary")
    private String summary;
  

    @Column(name = "created_at")
    private LocalDateTime createdAt;

      @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
