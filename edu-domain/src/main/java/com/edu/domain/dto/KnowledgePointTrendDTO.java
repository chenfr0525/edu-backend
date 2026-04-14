package com.edu.domain.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointTrendDTO {
   private String sourceName;                 // 作业名称或考试名称
    private String sourceType;                 // HOMEWORK/EXAM
    private LocalDateTime date;
    private BigDecimal scoreRate;              // 得分率
}
