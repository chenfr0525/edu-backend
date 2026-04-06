package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class GradeTrendDTO {
    private List<String> exams;
    private List<Double> scores;
    private List<Double> classAvg;
}
