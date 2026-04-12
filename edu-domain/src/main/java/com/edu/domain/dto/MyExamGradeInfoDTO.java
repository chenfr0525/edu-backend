package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

@Data
public class MyExamGradeInfoDTO {
   private Long gradeId;
    private Integer score;
    private String remark;
    private Integer classRank;
    private Integer gradeRank;
    private String scoreTrend;
    private Map<String, Integer> knowledgePointScores; // 各知识点得分
    private LocalDateTime createdAt;
}
