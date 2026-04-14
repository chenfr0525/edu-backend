package com.edu.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class AiSuggestionDTO {
    private String summary;
    private List<String> suggestions;
    private List<String> strengths;
    private List<String> weaknesses;
}
