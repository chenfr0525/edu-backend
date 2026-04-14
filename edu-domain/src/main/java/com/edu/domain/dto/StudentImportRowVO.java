// StudentImportRowVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class StudentImportRowVO {
    private Integer rowNum;                   // 行号
    private String studentNo;                 // 学号
    private String name;                      // 姓名
    private String username;                  // 用户名
    private String className;                 // 班级名称
    private String grade;                     // 年级
    private String gender;                    // 性别
    private String email;                     // 邮箱
    private String phone;                     // 手机号
    private Boolean isValid;                  // 是否有效
    private String errorMsg;                  // 错误信息
    private Map<String, Object> aiSuggest;    // AI解析建议
}