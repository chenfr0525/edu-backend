package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import com.edu.domain.CourseStatus;

@Data
@Builder
public class CourseDTO {
  private Long id;
  private String name;
  private String description;
  private String icon;
  private TeacherDTO teacher;
  private Integer credit;
  private CourseStatus status;
}
