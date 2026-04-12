package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class StudentExamDetailDTO {
   private Long id;
    private String name;
    private String type;
    private String description;
    private String courseName;
    private Long courseId;
    private LocalDateTime examDate;
    private String startTime;
    private String endTime;
    private Integer duration;
    private Integer fullScore;
    private Integer passScore;
    private String location;
    private String status;
    
    // 我的考试成绩
    private MyExamGradeInfoDTO myGrade;
    
    // 班级统计
    private ExamClassStatisticsDTO classStats;
    
    // 知识点得分分析
    private List<ExamKnowledgePointDTO> knowledgePointAnalysis;
    
    // 成绩分析
    private ExamScoreAnalysisDTO scoreAnalysis;
    
    // AI个性化建议
    private AiSuggestionDTO aiSuggestion;
}
