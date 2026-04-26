package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class ActivityTypeDistributionVO {
  private String type;
    private String typeName;
    private Integer count;
    private BigDecimal percentage;
}
