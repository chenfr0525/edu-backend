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
public class TeacherManageStatsVO {
    private Long totalTeacherCount;
    private Long activeTeacherCount;
    private Long adminCount;
    private List<Object[]> departmentStats;
    private List<Object[]> titleStats;
}
