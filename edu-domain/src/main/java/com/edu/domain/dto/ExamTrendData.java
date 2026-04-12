package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
public class ExamTrendData {
   private List<String> examNames;
    private List<Integer> myScores;
    private List<BigDecimal> classAvgs;
    private List<Integer> myRanks;
    private String courseName;
    private String trend;               // 上升/下降/稳定
    private Integer trendValue;
}
