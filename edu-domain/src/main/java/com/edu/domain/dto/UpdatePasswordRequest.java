package com.edu.domain.dto;

import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String newPassword;
    private String oldPassword;
}
