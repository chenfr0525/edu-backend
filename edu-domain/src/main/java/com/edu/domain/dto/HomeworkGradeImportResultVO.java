package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class HomeworkGradeImportResultVO {
   private Long homeworkId;
    private Integer totalImported;
    private Integer successCount;
    private Integer updateCount;
    private Integer failCount;
    private boolean success;
    private String message;
    private Boolean aiAnalysisCompleted;
}
