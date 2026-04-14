package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WrongQuestionDTO {
    private Long knowledgePointId;
    private String knowledgePointName;
    private Long courseId;
    private String courseName;
    private Integer errorCount;
    private Integer totalStudents;
    private BigDecimal errorRate;
    private Integer rank;
}