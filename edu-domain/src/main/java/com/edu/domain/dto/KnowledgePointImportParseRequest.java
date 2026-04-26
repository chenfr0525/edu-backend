package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class KnowledgePointImportParseRequest {
  private String fileContent;
    private String fileName;
    private Long courseId;
}
