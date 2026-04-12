package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class KnowledgePointMasteryDTO {
  private Long knowledgePointId;
    private String knowledgePointName;
    private Integer myScore;
    private Integer fullScore;
    private BigDecimal scoreRate;
    private BigDecimal classAvgRate;
    private String level;        // GOOD/MODERATE/WEAK
    private String suggestion;
}
