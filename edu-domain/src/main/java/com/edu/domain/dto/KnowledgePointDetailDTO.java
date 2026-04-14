package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointDetailDTO {
   private Long knowledgePointId;
    private String knowledgePointName;
    private String description;
    private Long courseId;
    private String courseName;
    private BigDecimal masteryRate;            // 我的掌握率
    private BigDecimal classAvgMasteryRate;    // 班级平均掌握率
    private String masteryLevel;               // good/warning/poor
    private List<String> weakPoints;           // 具体薄弱点
    private List<KnowledgePointTrendDTO> trendData;  // 掌握率趋势
    private List<KnowledgePointSourceDTO> sourceDetails;  // 来源详情（作业/考试）
}
