package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourseAiAnalysisVO {
  private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private Map<String, Object> analysisData;
    private Map<String, Object> chartsConfig;
    private LocalDateTime createdAt;
}
