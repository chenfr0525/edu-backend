// StudentInfoVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentInfoVO {
    private Long id;
    private String studentNo;   // 学号
    private String name;        // 姓名
    private String username;    // 用户名
    private String className;   // 班级名称
    private String grade;       // 年级
    private String email;       // 邮箱
    private String gender;      // 性别
    private String phone;       // 手机号
    private String avatar;      // 头像
    private Integer status;     // 状态 0-正常 1-冻结 2-待审核
    private String statusText;  // 状态文本
}