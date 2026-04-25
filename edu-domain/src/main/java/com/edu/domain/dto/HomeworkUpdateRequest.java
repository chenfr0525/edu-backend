package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.edu.domain.HomeworkStatus;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkUpdateRequest {
  private String name;
    
    private String description;
    private Long knowledgePointId;
    private Long courseId;
    private Integer questionCount;
    private Integer totalScore;
    private HomeworkStatus status;  // ONGOING/PENDING/COMPLETED/EXPIRED
    private LocalDateTime deadline;
}
