package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointStatisticsCardsDTO {
   private BigDecimal overallMasteryRate;     // 整体掌握率
    private Integer totalKnowledgePoints;      // 总知识点数
    private Integer weakKnowledgePoints;       // 薄弱知识点数（掌握率<60%）
    private Integer strongKnowledgePoints;     // 优势知识点数（掌握率>=80%）
    private String bestCourse;                 // 掌握最好的课程
    private String weakestCourse;              // 掌握最差的课程
}
