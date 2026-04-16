// ExamGradeImportResult.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExamGradeImportResult {
    private boolean success;
    private int successCount;
    private int failCount;
    private int updateCount;
    private String message;
    private String errorMessage;
    private boolean aiAnalysisCompleted;
    private List<ValidationError> errors;
}