package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RelatedWrongQuestionVO {
   private Long sourceId;
    private String sourceType;
    private String sourceName;
    private Integer errorCount;
    private Integer totalStudents;
    private BigDecimal errorRate;
}
