package com.edu.domain.dto;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class KnowledgePointErrorDTO {
    private Long knowledgePointId;
    private String knowledgePointName;
    private Integer errorCount;       // 错误人数
    private Integer totalStudents;    // 总学生数
    private BigDecimal errorRate;     // 错误率
    private String suggestion;        // 改进建议
}
