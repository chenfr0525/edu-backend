package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class CourseMasteryDTO {
   private Long courseId;
    private String courseName;
    private BigDecimal masteryRate;
    private String masteryLevel;
    private Integer knowledgePointCount;
}
