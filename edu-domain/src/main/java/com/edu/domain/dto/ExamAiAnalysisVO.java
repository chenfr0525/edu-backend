package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExamAiAnalysisVO {
    private String summary;              // 分析摘要
    private List<String> strengths;      // 优点
    private List<String> weaknesses;     // 薄弱点
    private List<String> suggestions;    // 改进建议
    private Map<String, Object> analysisData;  // 详细分析数据
    private Map<String, Object> chartsConfig;  // 图表配置
    private LocalDateTime createdAt;
}