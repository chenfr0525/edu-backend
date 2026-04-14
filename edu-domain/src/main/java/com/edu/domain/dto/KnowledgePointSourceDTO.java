package com.edu.domain.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointSourceDTO {
   private String sourceType;                 // HOMEWORK/EXAM
    private Long sourceId;
    private String sourceName;
    private BigDecimal myScoreRate;            // 我的得分率
    private BigDecimal classAvgScoreRate;      // 班级平均得分率
    private Integer myScore;
    private Integer fullScore;
}
