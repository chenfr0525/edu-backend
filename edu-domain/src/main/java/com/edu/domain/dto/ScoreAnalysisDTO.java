package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ScoreAnalysisDTO {
   private BigDecimal score;
    private BigDecimal classAvg;
    private BigDecimal diffFromAvg;
    private String trend;        // IMPROVING/STABLE/DECLINING/UNKNOWN
    private Integer rank;
    private ScoreDistributionDTO distribution;
}
