// ExamDetailVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.edu.domain.ExamStatus;

@Data
@Builder
public class ExamDetailVO {
    // 考试基本信息
    private Long id;
    private String name;
    private ExamStatus type;
    private String typeText;
    private String className;
    private String courseName;
    private Long courseId;
    private LocalDateTime examDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private Integer fullScore;
    private Integer passScore;
    private String location;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    
    // 统计数据（从exam表扩展字段获取）
    private BigDecimal classAvgScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    
    // 状态卡片数据
    private ExamDetailStatsVO stats;
    
    // 成绩分布数据
    private ScoreDistributionDTO scoreDistribution;
    
    // 学生成绩列表
    private List<ExamStudentGradeVO> studentGrades;
    
    // 知识点薄弱分析（基于knowledge_points_distribution）
    private List<ExamKnowledgePointDTO> knowledgePointAnalysis;
    
    // AI分析数据（从exam.ai_parsed_data获取）
    private ExamAiAnalysisVO aiAnalysis;
    
    // 知识点分值分布（从exam.knowledge_points_distribution获取）
    private Map<String, Object> knowledgePointsDistribution;
}