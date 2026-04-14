// StudentImportResultVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StudentImportResultVO {
    private Integer successCount;
    private Integer failCount;
    private List<String> errors;
}