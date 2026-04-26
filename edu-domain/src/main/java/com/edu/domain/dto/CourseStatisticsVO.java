package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CourseStatisticsVO {
   private Integer studentCount;        // 选课学生数
    private BigDecimal avgScore;         // 平均成绩
    private BigDecimal passRate;         // 及格率
    private Integer knowledgePointCount; // 知识点数量
}
