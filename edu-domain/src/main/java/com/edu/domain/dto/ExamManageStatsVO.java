// ExamManageStatsVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExamManageStatsVO {
    private Long totalExamCount;           // 总考试次数
    private BigDecimal overallAvgScore;    // 整体平均分
    private BigDecimal overallPassRate;    // 整体及格率
    private BigDecimal overallExcellentRate; // 整体优秀率
    private Integer totalStudentCount;     // 参考学生总数
    private Long completedCount;           // 已完成考试数
    private Long upcomingCount;            // 即将开始考试数
    private Long ongoingCount;             // 进行中考试数
}