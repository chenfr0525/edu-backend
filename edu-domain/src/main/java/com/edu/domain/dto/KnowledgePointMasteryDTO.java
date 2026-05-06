package com.edu.domain.dto;

import lombok.Data;

@Data
public class KnowledgePointMasteryDTO {
  private Long knowledgePointId;
    private String knowledgePointName;
    private Integer myScore;
    private Integer fullScore;
}
