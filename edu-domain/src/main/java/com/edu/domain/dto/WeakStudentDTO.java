package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeakStudentDTO {
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Double masteryLevel;
}