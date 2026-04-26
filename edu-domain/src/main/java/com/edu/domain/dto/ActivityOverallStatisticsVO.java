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
public class ActivityOverallStatisticsVO { 
  private Integer totalStudents;
    private BigDecimal avgActivityScore;
    private Integer highActivityCount;      // 高活跃学生数(>=70)
    private Integer lowActivityCount;       // 低活跃学生数(<50)
    private Integer criticalCount;          // 严重低活跃(<30)
}