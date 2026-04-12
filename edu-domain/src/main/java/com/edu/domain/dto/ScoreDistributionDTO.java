package com.edu.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ScoreDistributionDTO {
    private Integer excellentCount;  // 90-100
    private Integer goodCount;       // 80-89
    private Integer mediumCount;     // 70-79
    private Integer passCount;       // 60-69
    private Integer failCount;       // <60
    private BigDecimal averageScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
}
