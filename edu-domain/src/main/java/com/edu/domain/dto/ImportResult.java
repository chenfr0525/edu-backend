package com.edu.domain.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ImportResult {
   private boolean success;
    private int successCount;
    private int failCount;
    private List<String> errors;
    private String message;
    private String errorMessage;
    private String successDetails;
}
