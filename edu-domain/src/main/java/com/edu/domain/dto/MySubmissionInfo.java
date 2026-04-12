package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

@Data
public class MySubmissionInfo {
    private Long submissionId;
    private String content;
    private String attachments;
    private Double score;
    private String feedback;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
    private Boolean isLate;
    private Integer lateMinutes;
    private String aiFeedback;
    private Map<String, Integer> knowledgePointScores;
}