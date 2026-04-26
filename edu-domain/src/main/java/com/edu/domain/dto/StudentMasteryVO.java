package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StudentMasteryVO {
  private Long studentId;
    private String studentNo;
    private String studentName;
    private BigDecimal masteryLevel;
    private String weaknessLevel;
    private LocalDateTime lastUpdateTime;
}
