package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HomeworkListVO {
   private Long id;
    private String name;
    private String courseName;
    private Long courseId;
    private String className;
    private Long classId;
    private Integer totalScore;
    private Integer questionCount;
    private String status;
    private String statusText;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private BigDecimal avgScore;
    private BigDecimal passRate;
    private Integer submittedCount;
    private Integer totalStudents;
    private Boolean hasAiAnalysis;
    private String description;
}
