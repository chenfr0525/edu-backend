package com.edu.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomeworkDetailDTO {
    private Long id;
    private String name;
    private String description;
    private String courseName;
    private Long courseId;
    private Integer totalScore;
    private Integer questionCount;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private BigDecimal avgScore;
    private BigDecimal passRate;
    private Integer submissionCount;
    private Integer totalStudents;
    private String submitRate;
    
    // 成绩分布
    private ScoreDistributionDTO scoreDistribution;
    
    // 知识点错题分析
    private List<KnowledgePointErrorDTO> knowledgePointErrors;
    
    // AI分析数据
    private HomeworkAiAnalysisDTO aiAnalysis;
}
