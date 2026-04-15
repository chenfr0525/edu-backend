// ExamInfoVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.edu.domain.ExamStatus;

@Data
@Builder
public class ExamInfoVO {
    private Long id;
    private Long courseId;
    private Long classId;
    private String description;
    private String name;
    private ExamStatus type;
    private String typeText;
    private String className;
    private String courseName;
    private LocalDateTime examDate;
    private Integer fullScore;
    private Integer passScore;
    private ExamStatus status;
    private String statusText;
    private Integer studentCount;      // 参考人数
    private BigDecimal avgScore;       // 平均分
    private BigDecimal passRate;       // 及格率
    private BigDecimal highestScore;   // 最高分
    private LocalDateTime createdAt;
    private Boolean hasAiAnalysis;     // 是否已有AI分析
}