package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class StudentActivityRankVO {
  private Integer rank;
    private Long studentId;
    private String studentName;
    private BigDecimal activityScore;
    private Boolean isCurrentStudent;
}
