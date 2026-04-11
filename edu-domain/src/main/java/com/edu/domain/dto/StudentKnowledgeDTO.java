package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentKnowledgeDTO {
  private Long id;
  private StudentDTO student;
  private KnowledgePointDTO knowledgePoint;
  private Integer masteryLevel;
  private Double score;
  private LocalDateTime lastPracticeTime;
}
