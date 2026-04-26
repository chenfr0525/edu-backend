package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CourseDetailVO {
   private Long id;
    private String name;
    private String description;
    private String icon;
    private String teacherName;
    private Long teacherId;
    private Integer credit;
    private String status;
    private LocalDateTime createdAt;
    private CourseStatisticsVO statistics;
}
