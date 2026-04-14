// StudentListRequest.java
package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentListRequest {
    private Long classId;      // 班级ID，为空则查询所有班级
    private Long courseId;     // 课程ID，为空则查询所有课程
    private String keyword;    // 模糊查询（姓名/学号/用户名）
    private Integer page;      // 页码，默认0
    private Integer size;      // 每页大小，默认10
}