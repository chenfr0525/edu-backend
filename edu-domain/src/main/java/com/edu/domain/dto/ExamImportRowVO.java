// ExamImportRowVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class ExamImportRowVO {
    private Integer rowNum;
    private String studentNo;
    private String studentName;
    private Integer score;
    private String remark;
    private Boolean isValid;
    private String errorMsg;
    private Map<String, Object> knowledgePointScores; // AI解析的知识点得分
}