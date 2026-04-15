// ExamImportConfirmRequest.java
package com.edu.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExamImportConfirmRequest {
    private String fileId;
    private Long examId;                 // 已存在的考试ID（更新成绩）
    private List<Integer> selectedRowIndexes;
}