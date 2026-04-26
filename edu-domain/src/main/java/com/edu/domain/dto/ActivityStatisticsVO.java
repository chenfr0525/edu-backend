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
public class ActivityStatisticsVO {
  private Integer totalLoginCount;        // 总登录次数
    private Integer totalHomeworkCount;     // 总作业提交次数
    private Integer totalExamCount;         // 总考试参与次数
    private Integer totalStudyDuration;     // 总学习时长（分钟）
    private Integer totalResourceCount;     // 总资源访问次数
    private BigDecimal avgActivityScore;    // 平均活跃度得分
    private String activityLevel;           // 活跃等级
    private String compareToClass;          // 与班级平均对比
}
