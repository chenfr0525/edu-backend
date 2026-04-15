package com.edu.domain.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 学生导入DTO
 */
@Data
public class StudentImportDTO {
    private String studentNo;      // 学号，必填，唯一
    private String name;           // 姓名，必填
    private String username;       // 用户名，必填，唯一
    private String classname;      // 班级名称，必填
    private String grade;          // 年级，必填
    private String email;          // 邮箱，可选
    private String gender;         // 性别，必填
    private String phone;          // 电话，可选
    private String status;         // 状态，默认ACTIVE
    private String password;       // 密码，可选，默认123456
}