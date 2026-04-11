package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.edu.domain.ClassInfo;
import com.edu.domain.Teacher;

import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassInfoDTO {
    private Long id;
    private String className;
    private String grade;
    private Teacher teacher;
    private LocalDateTime createdAt;

     public ClassInfoDTO(ClassInfo classInfo, Teacher teacher) {
        if (classInfo != null) {
            this.id = classInfo.getId();
            this.className = classInfo.getName();
            this.grade = classInfo.getGrade();
            this.createdAt = classInfo.getCreatedAt();
        }
        if (teacher != null) {
            this.teacher = teacher;
        }
    }

   }
