// ExamStudentGradeVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class ExamStudentGradeVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private BigDecimal score;
    private Integer classRank;
    private String remark;
    private String scoreTrend;           // UP/STABLE/DOWN
}