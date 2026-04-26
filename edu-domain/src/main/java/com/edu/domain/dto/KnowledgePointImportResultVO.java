package com.edu.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgePointImportResultVO {
  private Integer totalImported;
    private Integer successCount;
    private Integer failCount;
    private List<String> errors;
    private boolean success;
    private String message;
}
