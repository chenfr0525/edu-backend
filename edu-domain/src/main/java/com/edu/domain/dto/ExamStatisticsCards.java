package com.edu.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ExamStatisticsCards {
   private BigDecimal avgScore;        // 考试平均分
    private BigDecimal avgRank;         // 平均排名（排名越小越好）
    private Integer totalExams;         // 总考试次数
    private Integer aboveAvgCount;      // 高于班级平均的考试次数
    private BigDecimal aboveAvgRate;    // 高于班级平均的比例
    private String bestSubject;         // 最佳科目
    private String weakestSubject;      // 薄弱科目
}
