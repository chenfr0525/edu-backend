package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointTreeDTO {
  private String id;                    // 知识点ID（格式：courseId 或 courseId-kpId）
    private String label;                 // 显示名称（带图标）
    private BigDecimal masteryRate;       // 掌握率（0-100）
    private String masteryLevel;          // good/warning/poor
    private List<String> weakPoints;      // 薄弱点列表
    private List<KnowledgePointTreeDTO> children;  // 子知识点
    
    // 课程级别额外字段
    private Long courseId;                // 课程ID
    private String courseName;            // 课程名称
}
