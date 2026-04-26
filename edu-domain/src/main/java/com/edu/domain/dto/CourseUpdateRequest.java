package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CourseUpdateRequest {
   private String name;
    private String description;
    private String icon;
    private Long teacherId;
    private Integer credit;
    private String status;
}
