package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CourseChartDataVO {
   // 成绩趋势图
    private List<ScoreTrendItem> scoreTrend;
    
    // 知识点掌握度雷达图
    private RadarChartData radarChart;
    
    // 成绩分布饼图
    private ScoreDistributionPie scoreDistribution;
    
    // 作业/考试对比柱状图
    private List<ComparisonItem> homeworkExamComparison;

    @Data
@Builder
      public static class ScoreTrendItem {
      private String name;
      private BigDecimal score;
      private String type;
      private String date;
    }
@Data
@Builder
  public static class RadarChartData {
      private List<String> indicators;
      private List<BigDecimal> classAvg;
  }

  @Data
@Builder
  public static class ScoreDistributionPie {
      private int excellent;
      private int good;
      private int medium;
      private int pass;
      private int fail;
  }
@Data
@Builder
  public static class ComparisonItem {
      private String name;
      private BigDecimal score;
  }
}
