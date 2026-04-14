package com.edu.domain.dto;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ClassScoreDistributionDTO {
    private Long classId;
    private String className;
    private String grade;
    private Map<String, Integer> distribution;  // 成绩分布
    private Double averageScore;
    private Double highestScore;
    private Double lowestScore;
    private Double passRate;
    private Double excellentRate;
    private Integer studentCount;
    private Double standardDeviation;
}