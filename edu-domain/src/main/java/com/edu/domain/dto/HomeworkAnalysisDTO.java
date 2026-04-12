package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class HomeworkAnalysisDTO {
   private Long id;
    private String name;
    private String courseName;
    private Long courseId;
    private Integer totalScore;
    private Integer questionCount;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private BigDecimal avgScore;        // 班级平均分
    private BigDecimal passRate;        // 及格率
    private Integer submissionCount;    // 提交人数
    private Integer totalStudents;      // 应提交人数
    private String submitRate;          // 提交率
}