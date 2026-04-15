// ExamStudentGradePageRequest.java
package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStudentGradePageRequest {
    private Integer page;          // 页码，默认0
    private Integer size;          // 每页大小，默认10
    private String keyword;        // 学生姓名/学号模糊查询
    private String sortBy;         // 排序字段: score/studentNo/studentName/classRank
    private String sortOrder;      // 排序方向: asc/desc，默认desc（按分数降序）
}