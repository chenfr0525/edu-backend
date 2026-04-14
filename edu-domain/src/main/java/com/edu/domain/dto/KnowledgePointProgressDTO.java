package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointProgressDTO {
   private BigDecimal overallMasteryRate;     // 整体掌握率
    private Integer masteredCount;             // 已掌握知识点数（>=80%）
    private Integer learningCount;             // 学习中知识点数（60%-80%）
    private Integer weakCount;                 // 薄弱知识点数（<60%）
    private List<CourseMasteryDTO> courseMasteryList;  // 各课程掌握情况
}
