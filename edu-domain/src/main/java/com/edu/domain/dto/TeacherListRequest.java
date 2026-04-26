package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

import com.edu.domain.Teacher;
import com.edu.domain.User;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherListRequest {
   private String keyword;
    private String department;
    private String status;
    private Integer page = 0;
    private Integer size = 10;
}
