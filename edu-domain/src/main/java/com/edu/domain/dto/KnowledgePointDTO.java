package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class KnowledgePointDTO {
  private Long id;
  private String name;
  private String description;
  private List<KnowledgePointDTO> children;
  private Integer level;
  private Integer sortOrder;
  private CourseDTO course;
}
