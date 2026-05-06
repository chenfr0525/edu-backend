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
    private BigDecimal classAvgScore;
}
