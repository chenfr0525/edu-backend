package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgePointVO {
   private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String parentName;
    private Integer level;
    private Integer sortOrder;
    private Integer childCount;           // 子知识点数量
    private BigDecimal classAvgMastery;   // 班级平均掌握度
    private String weaknessLevel;         // 薄弱程度
    private List<KnowledgePointVO> children;
}
