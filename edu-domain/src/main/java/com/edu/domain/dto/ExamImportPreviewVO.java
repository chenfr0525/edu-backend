// ExamImportPreviewVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExamImportPreviewVO {
    private String fileId;
    private String fileName;
    private Long examId;
    private String examName;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private List<ExamImportRowVO> rows;
    private List<String> errors;
    private Map<String, Object> aiAnalysis;   // AI解析的预览数据
}