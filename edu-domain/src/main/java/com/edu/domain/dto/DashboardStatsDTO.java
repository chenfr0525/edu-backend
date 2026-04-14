package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {
    private Long studentCount;           // 学生总数
    private Long teacherCount;           // 教师数（管理员用）
    private Long classCount;             // 班级数（管理员用）
    private Long courseCount;            // 课程数
    private Long pendingHomeworkCount;   // 待批改作业数
    private Long upcomingExamCount;      // 即将开始的考试数
    private Long lowActivityAlertCount;  // 低活跃度预警数
    private Long weakPointCount;         // 薄弱知识点数
    private Double overallAvgScore;      // 整体平均分
    private Double overallPassRate;      // 整体及格率
}