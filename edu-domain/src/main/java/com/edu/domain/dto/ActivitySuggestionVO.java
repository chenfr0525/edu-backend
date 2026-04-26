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
public class ActivitySuggestionVO {
  private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private String highestStudent;
    private String lowestStudent;
}
