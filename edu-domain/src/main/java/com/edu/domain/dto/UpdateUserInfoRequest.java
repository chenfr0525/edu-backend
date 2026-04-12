package com.edu.domain.dto;

import com.edu.domain.UserStatus;

import lombok.Data;

@Data
public class UpdateUserInfoRequest {
    private String username;
    private String name;
    private String gender;
    private String email;
    private String phone;
    private UserStatus status;
}
