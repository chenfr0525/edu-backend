// StudentImportConfirmRequest.java
package com.edu.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class StudentImportConfirmRequest {
    private String fileId;                    // 临时文件ID
    private List<Long> selectedRowIndexes;    // 选中的行索引（为空则全部导入）
}