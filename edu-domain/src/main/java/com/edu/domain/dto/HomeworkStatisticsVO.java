package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HomeworkStatisticsVO {
   private Long totalHomework;      // 作业总数
    private BigDecimal avgScore;      // 平均分
    private BigDecimal avgPassRate;   // 平均及格率
    private BigDecimal onTimeRate;    // 按时提交率
}
