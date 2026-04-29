package com.edu.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestionDTO {
    private String summary;
    private List<String> suggestions;
    private List<String> strengths;
    private List<String> weaknesses;
}
