// ExamListRequest.java
package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamListRequest {
    private Long classId;      // 班级ID
    private Long courseId;     // 课程ID
    private String keyword;    // 考试名称模糊查询
    private Integer page;
    private Integer size;
}