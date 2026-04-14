package com.edu.domain.dto;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeachingDashboardRequest {
    private Long classId;      // 班级ID，为空则查询所有班级
    private Long courseId;     // 课程ID，为空则查询所有课程
}
