package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TeachingDashboardDataDTO {
    // 筛选条件信息
    private List<Long> selectedClassIds;
    private List<Long> selectedCourseIds;
    
    // 状态卡片
    private DashboardStatsDTO stats;
    
    // 成绩分布（可多个班级）
    private List<ClassScoreDistributionDTO> scoreDistributions;
    
    // 高频错题排行
    private List<WrongQuestionDTO> topWrongQuestions;
    
    // 薄弱知识点
    private List<WeakKnowledgePointDTO> weakKnowledgePoints;
    
    // 活跃度监控
    private ActivityMonitorDTO activityMonitor;
}