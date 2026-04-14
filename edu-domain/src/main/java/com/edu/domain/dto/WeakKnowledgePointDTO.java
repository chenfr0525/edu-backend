package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WeakKnowledgePointDTO {
    private Long knowledgePointId;
    private String knowledgePointName;
    private Long courseId;
    private String courseName;
    private Double avgMastery;
    private Integer studentCount;
    private Double affectedRate;
    private List<WeakStudentDTO> weakStudents;  // 该知识点薄弱的学生
}