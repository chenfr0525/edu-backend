package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class StudentActivityDetailRequest {
  private String keyword;         // 模糊搜索
    private Integer page = 0;
    private Integer size = 10;
}
