package com.edu.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class HomeworkAiAnalysisDTO {
    private String summary;                    // 分析摘要
    private List<String> strengths;            // 优点
    private List<String> weaknesses;           // 薄弱点
    private List<String> suggestions;          // 改进建议
    private Map<String, BigDecimal> knowledgePointScores; // 各知识点得分率
    private String detailedReport;             // 详细报告
}