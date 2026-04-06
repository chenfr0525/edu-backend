package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class KnowledgeMasteryDTO {
    private List<Integer> current;
    private List<Integer> classAvg;
    private List<String> indicators;
}
