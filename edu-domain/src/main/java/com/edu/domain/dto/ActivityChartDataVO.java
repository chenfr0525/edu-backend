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
public class ActivityChartDataVO {
   // 活跃度排行榜（Top10）
    private List<RankingItem> activityRanking;
    
    // 不活跃学生预警列表
    private List<WarningItem> lowActivityWarnings;
    
    // 各班级平均活跃度对比
    private List<ClassActivityVO> classComparison;
    
    // 活跃度趋势（近7天/30天）
    private List<TrendItem> trendData;
}
