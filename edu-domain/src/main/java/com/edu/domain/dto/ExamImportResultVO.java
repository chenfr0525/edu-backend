// ExamImportResultVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExamImportResultVO {
    private Long examId;
    private String examName;
    private Integer totalImported;
    private Integer successCount;
    private Integer failCount;
    private List<String> errors;
    private Boolean aiAnalysisCompleted;
}