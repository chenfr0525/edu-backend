package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class HomeworkAiAnalysisVO {
  private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private Map<String, Object> analysisData;
    private Map<String, Object> chartsConfig;
    private LocalDateTime createdAt;
}
