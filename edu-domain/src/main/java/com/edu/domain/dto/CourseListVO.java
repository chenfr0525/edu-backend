package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CourseListVO {
   private Long id;
    private String name;
    private String description;
    private String icon;
    private String teacherName;
    private Long teacherId;
    private Integer credit;
    private String status;
    private String statusText;
    private Integer studentCount;      // 选课学生数
    private Integer knowledgePointCount; // 知识点数量
    private Integer examCount;          // 考试次数
    private Integer homeworkCount;      // 作业次数
    private LocalDateTime createdAt;
}
