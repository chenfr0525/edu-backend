package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentStatsDTO {
    private Integer courseCount;
    private Integer studyHours;
    private Double avgScore;
    private Integer classRank;
    private Integer totalStudents;
}
