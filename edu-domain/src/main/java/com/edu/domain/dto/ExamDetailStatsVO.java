// ExamDetailStatsVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExamDetailStatsVO {
    private Integer totalStudents;      // 参考人数
    private Integer submittedCount;     // 已录入成绩人数
    private BigDecimal avgScore;        // 平均分
    private BigDecimal highestScore;    // 最高分
    private BigDecimal lowestScore;     // 最低分
    private BigDecimal passRate;        // 及格率
    private BigDecimal excellentRate;   // 优秀率(>=80)
    private BigDecimal medianScore;     // 中位数
    private BigDecimal standardDeviation; // 标准差
}