package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HomeworkDetailVO {
   private Long id;
    private String name;
    private String description;
    private String courseName;
    private Long courseId;
    private String className;
    private Long classId;
    private Integer totalScore;
    private Integer questionCount;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private BigDecimal avgScore;
    private BigDecimal passRate;
    
    // 统计信息
    private Integer totalStudents;
    private Integer submittedCount;
    private String submitRate;
    private Integer onTimeCount;
    
    // 成绩分布
    private ScoreDistributionDTO scoreDistribution;
    
    // 学生成绩列表
    private List<HomeworkStudentGradeVO> studentGrades;
    
    // 知识点错题分析
    private List<KnowledgePointErrorVO> knowledgePointErrors;
    
    // AI分析
    private HomeworkAiAnalysisVO aiAnalysis;
}
