package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HomeworkStudentGradeVO {
   private Long studentId;
    private String studentNo;
    private String studentName;
    private Double score;
    private String feedback;
    private String status;
    private Boolean isLate;
    private Integer lateMinutes;
    private LocalDateTime submittedAt;
}
