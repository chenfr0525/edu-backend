// ExamCreateRequest.java
package com.edu.domain.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.edu.domain.ExamStatus;

@Data
public class ExamCreateRequest {
    private String name;           // 考试名称（必填）
    private ExamStatus type;           // 考试类型（必填）
    private Long classId;          // 班级ID（必填）
    private Long courseId;         // 课程ID（必填）
    private LocalDateTime examDate;    // 考试日期（必填）
    private LocalDateTime startTime;   // 开始时间
    private LocalDateTime endTime;     // 结束时间
    private Integer duration;      // 时长（分钟）
    private Integer fullScore;     // 总分，默认100
    private Integer passScore;     // 及格分，默认60
    private String location;       // 考试地点
    private String description;    // 考试说明
    private List<Long> knowledgePointIds;// 知识点ID列表[1,2,3]
}