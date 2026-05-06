package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class HomeworkStatisticsCards {
   private BigDecimal avgScore;        // 作业平均分
    private Long aboveAvgCount;         // 高于班级平均的作业数
    private Long completedCount;        // 已完成作业数
    private Long totalCount;            // 总作业数
}
