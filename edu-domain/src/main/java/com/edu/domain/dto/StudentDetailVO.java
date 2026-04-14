package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentDetailVO {
    private Long id;
    private String studentNo;
    private String name;
    private String username;
    private String className;
    private String grade;
    private String gender;
    private String email;
    private String phone;
    private String avatar;
    private String status;
    private String createdAt;
}