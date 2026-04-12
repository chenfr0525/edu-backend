package com.edu.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学生作业列表DTO
 */
@Data
public class StudentHomeworkDTO {
    private Long id;
    private String name;
    private String courseName;
    private Long courseId;
    private Integer totalScore;
    private Integer questionCount;
    private String status;           // ONGOING/COMPLETED/EXPIRED
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private Double myScore;         // 我的得分
    private BigDecimal classAvgScore; // 班级平均分
    private String submitStatus;     // PENDING/SUBMITTED/GRADED
    private Boolean isLate;          // 是否迟交
    private Integer rank;            // 班级排名（如果有）
}