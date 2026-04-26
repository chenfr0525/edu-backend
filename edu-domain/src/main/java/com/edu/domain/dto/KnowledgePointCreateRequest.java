package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePointCreateRequest {
  private String name;
    private String description;
    private Long courseId;
    private Long parentId;  // 父知识点ID，可为空
    private Integer level = 0;
    private Integer sortOrder = 0;
}
