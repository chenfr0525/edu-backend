package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class StudentHomeworkDetailDTO {
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
    
    // 我的提交信息
    private MySubmissionInfo mySubmission;
    
    // 班级统计
    private ClassStatisticsDTO classStats;
    
    // 知识点掌握分析
    private List<KnowledgePointMasteryDTO> knowledgePointAnalysis;
    
    // 成绩分析
    private ScoreAnalysisDTO scoreAnalysis;
    
    // AI个性化建议
    private AiSuggestionDTO aiSuggestion;
}
