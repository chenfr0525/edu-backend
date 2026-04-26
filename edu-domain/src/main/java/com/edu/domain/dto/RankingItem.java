package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RankingItem {
    private Integer rank;
    private Long studentId;
    private String studentName;
    private String className;
    private BigDecimal activityScore;
    private Integer loginCount;
    private Integer studyDuration;
}
