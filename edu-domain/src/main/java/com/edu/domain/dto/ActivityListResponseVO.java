package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class ActivityListResponseVO {
  private List<StudentActivityVO> records;
    private Long total;
    private Integer current;
    private Integer size;
    private Integer pages;
    private ActivityOverallStatisticsVO overallStats;
}
