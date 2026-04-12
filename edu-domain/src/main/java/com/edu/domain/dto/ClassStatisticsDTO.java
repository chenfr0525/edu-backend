package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ClassStatisticsDTO {
   private Integer totalStudents;
    private Integer submittedCount;
    private BigDecimal avgScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private Integer myRank;
    private BigDecimal passRate;
    private BigDecimal excellentRate;
}
