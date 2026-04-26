package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class StudentActivityDetailVO {
  private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private Long classId;
    
    // 统计卡片（4个）
    private ActivityStatisticsVO statistics;
    
    // 活跃度趋势图数据
    private List<ActivityTrendVO> trendData;
    
    // 各类型活动分布
    private List<ActivityTypeDistributionVO> typeDistribution;
    
    // 活跃度排行榜（同班级）
    private List<StudentActivityRankVO> classRanking;
    
    // 分析建议
    private ActivitySuggestionVO suggestion;
}
