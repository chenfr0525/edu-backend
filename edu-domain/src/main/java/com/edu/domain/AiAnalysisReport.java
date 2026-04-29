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
     * 目标类型: STUDENT/CLASS/COURSE/EXAM/HOMEWORK
     */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /**
     * 目标ID(学生ID/班级ID/课程ID/考试ID/作业ID)
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    
    @ManyToOne
    @JoinColumn(name = "semester_id")
    private Semester semester;
    
    /**
     * 报告类型: EXAM_ANALYSIS/HOMEWORK_ANALYSIS/KNOWLEDGE_ANALYSIS/COMPREHENSIVE
     */
    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType;
    
    /**
     * AI分析数据(JSON格式) - 存储源数据的JSON
     */
    @Column(name = "analysis_data", columnDefinition = "TEXT")
    private String analysisData;
    
    /**
     * 可视化图表配置(JSON格式)
     */
    @Column(name = "charts_config", columnDefinition = "TEXT")
    private String chartsConfig;
    
    /**
     * AI生成的个性化建议（换行分隔多条建议）
     */
    @Column(name = "suggestions", columnDefinition = "TEXT")
    private String suggestions;
    
    /**
     * 分析摘要
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    /**
     * 优势列表(JSON数组)
     */
    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;
    
    /**
     * 不足列表(JSON数组)
     */
    @Column(name = "weaknesses", columnDefinition = "TEXT")
    private String weaknesses;
    
    /**
     * 源数据的MD5哈希值，用于判断数据是否变化
     */
    @Column(name = "data_hash", length = 64)
    private String dataHash;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}