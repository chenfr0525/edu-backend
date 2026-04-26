package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class ActivityStudentListRequest {
  private Long classId;           // 班级ID（可选）
    private String keyword;         // 学生姓名/学号模糊搜索
    private Integer page = 0;
    private Integer size = 10;
}
