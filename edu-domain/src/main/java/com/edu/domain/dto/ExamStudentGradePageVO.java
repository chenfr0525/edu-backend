package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExamStudentGradePageVO {
    private List<ExamStudentGradeVO> records;  // 改为 List，不是 Page
    private Long total;
    private Integer current;
    private Integer size;
    private Integer pages;
    private Statistics statistics;
    
    @Data
    @Builder
    public static class Statistics {
        private Integer totalStudents;
        private Double avgScore;
        private Double highestScore;
        private Double lowestScore;
        private Double passRate;
        private Double excellentRate;
        private Integer passCount;
        private Integer excellentCount;
        private Integer failCount;
    }
}