package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ExamClassStatisticsDTO {
   private Integer totalStudents;
    private BigDecimal avgScore;
    private BigDecimal highestScore;
    private BigDecimal lowestScore;
    private BigDecimal passRate;
    private BigDecimal excellentRate;  // 优秀率(>=80)
    private Integer myRank;
}
