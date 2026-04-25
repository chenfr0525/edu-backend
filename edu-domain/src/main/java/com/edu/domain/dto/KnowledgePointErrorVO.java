package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgePointErrorVO {
   private Long knowledgePointId;
    private String knowledgePointName;
    private Integer errorCount;
    private Integer totalStudents;
    private BigDecimal errorRate;
    private String suggestion;
}
