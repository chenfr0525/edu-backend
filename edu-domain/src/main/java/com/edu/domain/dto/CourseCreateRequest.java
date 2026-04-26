package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor  // 添加这个 - 无参构造
@AllArgsConstructor // 可选 - 全参构造
public class CourseCreateRequest {
    private String name;
    private String description;
    private String icon;
    private Long teacherId;
    private Integer credit = 2;
    private String status = "ONGOING";
}
