package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeacherStatsDTO {
    private Integer studentCount;
    private Double avgScore;
    private Double passRate;
    private Double excellentRate;
    private Integer pendingHomework;
    private Integer lowScoreCount;
    private Integer highScoreCount;
}
