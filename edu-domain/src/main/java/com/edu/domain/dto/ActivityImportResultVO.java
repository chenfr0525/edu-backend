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
public class ActivityImportResultVO {
   private Integer totalImported;
    private Integer successCount;
    private Integer failCount;
    private List<String> errors;
    private boolean success;
    private String message;
}
