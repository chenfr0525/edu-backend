// StudentManageStatsVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentManageStatsVO {
    private Long totalStudentCount;      // 学生总数
    private Long activeStudentCount;     // 活跃学生数（近7天有活动）
    private Long lowActivityCount;       // 低活跃度学生数（活跃度<20）
    private Long weakPointStudentCount;  // 有薄弱知识点的学生数
    private Double avgActivityScore;     // 平均活跃度得分
    private Double avgExamScore;         // 平均考试成绩
    private Long maleCount;              // 男生人数
    private Long femaleCount;            // 女生人数
}