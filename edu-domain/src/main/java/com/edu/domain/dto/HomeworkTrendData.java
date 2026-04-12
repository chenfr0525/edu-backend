package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class HomeworkTrendData {
  private List<String> homeworkNames;
    private List<BigDecimal> myScores;
    private List<BigDecimal> classAvgs;
    private String courseName;
    private String trend;        // 上升/下降/稳定/数据不足
    private BigDecimal trendValue;
}
