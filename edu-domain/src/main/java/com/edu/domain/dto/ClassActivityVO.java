package com.edu.domain.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassActivityVO {
  private Long classId;
    private String className;
    private BigDecimal avgActivityScore;
    private Integer studentCount;
    private Integer highActivityCount;
    private Integer lowActivityCount;
}
