package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LowActivityWarningVO {
  private Long studentId;
    private String studentName;
    private String studentNo;
    private String className;
    private BigDecimal activityScore;
    private String warningLevel;
    private String warningReason;
    private String suggestion;
    private LocalDateTime lastActiveTime;
}
