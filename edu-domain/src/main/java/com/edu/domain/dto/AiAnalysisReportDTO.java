package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiAnalysisReportDTO {
    private Long reportId;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String reportType;
    private String summary;           // 分析摘要
    private String suggestions;       // 个性化建议
    private List<String> keyFindings; // 关键发现
    private List<String> riskWarnings;// 风险预警
    private List<String> actionItems; // 行动建议
    private LocalDateTime createdAt;
}