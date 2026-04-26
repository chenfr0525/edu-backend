package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class StudentActivityVO {
  private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private Long classId;
    
    // 五种活跃度指标
    private Integer loginCount;         // 登录次数
    private LocalDateTime lastLoginTime;// 最后登录时间
    private Integer homeworkCount;      // 作业提交次数
    private Integer examCount;          // 考试参与次数
    private Integer studyDuration;      // 学习时长（分钟）
    private Integer resourceAccessCount;// 资源访问次数
    
    // 综合评分
    private BigDecimal activityScore;   // 活跃度得分(0-100)
    private String activityLevel;       // 活跃等级: HIGH/MEDIUM/LOW/CRITICAL
}
