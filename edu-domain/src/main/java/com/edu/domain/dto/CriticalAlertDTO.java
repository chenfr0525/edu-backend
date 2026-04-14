package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CriticalAlertDTO {
    private Long alertId;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String alertType;
    private String alertLevel;
    private BigDecimal activityScore;
    private BigDecimal threshold;
}