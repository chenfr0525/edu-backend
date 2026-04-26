package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgePointStatsVO {
  private BigDecimal classAvgMastery;      // 班级平均掌握度
    private BigDecimal highestMastery;       // 最高掌握度
    private BigDecimal lowestMastery;        // 最低掌握度
    private Integer masteredCount;           // 掌握良好人数(>=70)
    private Integer weakCount;               // 薄弱人数(<50)
    private Integer totalStudents;           // 总学生数
}
