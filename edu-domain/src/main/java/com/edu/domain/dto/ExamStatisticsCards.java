package com.edu.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ExamStatisticsCards {
   private BigDecimal avgScore;        // 考试平均分
    private Integer totalExams;         // 总考试次数
    private Integer completedExams;     // 已完成的考试次数
    private Integer aboveAvgCount;      // 高于班级平均的考试次数
}
