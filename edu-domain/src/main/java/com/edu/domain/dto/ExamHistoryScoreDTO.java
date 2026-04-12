package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ExamHistoryScoreDTO {
   private Long examId;
    private String examName;
    private LocalDateTime examDate;
    private Integer myScore;
    private BigDecimal classAvg;
}
