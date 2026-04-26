package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MasteryTrendVO {
  private String date;
    private BigDecimal masteryLevel;
    private String sourceType;  // HOMEWORK/EXAM
    private String sourceName;  // 作业/考试名称
}
