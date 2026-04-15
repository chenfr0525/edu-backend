package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ExamKnowledgePointDTO {
   private Long knowledgePointId;
    private String knowledgePointName;
    private Integer myScore;
    private Integer fullScore;
    private BigDecimal scoreRate;      // 我的得分率
    private BigDecimal classAvgRate;   // 班级平均得分率
    private String level;              // GOOD/MODERATE/WEAK
    private String suggestion;
}
