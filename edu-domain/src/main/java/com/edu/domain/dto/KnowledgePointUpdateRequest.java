package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePointUpdateRequest {
   private String name;
    private String description;
    private Long parentId;
    private Integer level;
    private Integer sortOrder;
}
