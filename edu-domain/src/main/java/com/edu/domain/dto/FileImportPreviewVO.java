// FileImportPreviewVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class FileImportPreviewVO {
    private String fileId;                    // 临时文件ID
    private String fileName;                  // 原文件名
    private Integer totalRows;                // 总行数
    private Integer validRows;                // 有效行数
    private Integer invalidRows;              // 无效行数
    private List<StudentImportRowVO> rows;    // 预览数据行
    private List<String> errors;              // 解析错误信息
    private Map<String, Object> aiAnalysis;   // AI解析的额外数据
}