package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class HomeworkStatisticsCards {
   private BigDecimal avgScore;        // 作业平均分
    private BigDecimal completionRate;  // 作业完成率
    private BigDecimal onTimeRate;      // 按时提交率
    private Long aboveAvgCount;         // 高于班级平均的作业数
    private BigDecimal aboveAvgRate;    // 高于班级平均的比例
    private Long completedCount;        // 已完成作业数
    private Long totalCount;            // 总作业数
}
