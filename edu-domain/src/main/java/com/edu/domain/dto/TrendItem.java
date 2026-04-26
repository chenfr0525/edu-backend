package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrendItem {
  private String date;
    private BigDecimal avgActivityScore;
    private Integer totalLoginCount;
    private Integer totalStudyDuration;
}
