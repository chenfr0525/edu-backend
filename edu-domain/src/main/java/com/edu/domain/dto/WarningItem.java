package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarningItem {
  private Long studentId;
    private String studentName;
    private String className;
    private BigDecimal activityScore;
    private String warningLevel;    // CRITICAL/WARNING
    private String warningReason;
    private String suggestion;
}
