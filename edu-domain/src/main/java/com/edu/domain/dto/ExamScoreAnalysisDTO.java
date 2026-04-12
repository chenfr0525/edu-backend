package com.edu.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ExamScoreAnalysisDTO {
   private Integer myScore;
    private BigDecimal classAvg;
    private Integer diffFromAvg;       // 与平均分差距
    private String trend;              // IMPROVING/STABLE/DECLINING
    private Integer rank;
    private ScoreDistributionDTO distribution;
    private List<ExamHistoryScoreDTO> historyScores; // 历次同类考试对比
}
