package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class StudentExamDTO {
    private Long id;
    private String name;
    private String type;              // MOCK/UNIT/MONTHLY/MIDTERM/FINAL
    private String courseName;
    private Long courseId;
    private LocalDateTime examDate;
    private Integer fullScore;
    private Integer myScore;          // 我的得分
    private BigDecimal classAvgScore; // 班级平均分
    private Integer classRank;        // 班级排名
    private String status;            // UPCOMING/ONGOING/COMPLETED
    private String scoreTrend;        // UP/STABLE/DOWN
}
