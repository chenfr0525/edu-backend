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
public class ActivityTrendVO {
  private String date;
    private Integer loginCount;
    private Integer homeworkCount;
    private Integer studyDuration;
    private Integer resourceCount;
    private BigDecimal activityScore;
}
