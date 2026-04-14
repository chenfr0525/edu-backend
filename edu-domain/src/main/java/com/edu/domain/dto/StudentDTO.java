package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import com.edu.domain.ClassInfo;
import com.edu.domain.Student;
import com.edu.domain.User;

import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDTO {
    
    private Long id;
    private String studentNo;   // 学号
    private String name;        // 姓名
    private String username;    // 用户名
    private Long classId;   // 班级
    private String grade;       // 年级
    private String email;       // 邮箱
    private String gender;       // 性别
    private String phone;       // 手机号
    private String avatar;      // 头像
    private Integer status;     // 状态

     public StudentDTO(Student student,User user,ClassInfo classInfo) {
        if (student != null) {
            this.id = student.getId();
            this.studentNo = student.getStudentNo();
            this.grade = student.getGrade();
            
        }
        if (user != null) {
            this.name = user.getName();
            this.username = user.getUsername();
            this.email = user.getEmail();
            this.gender = user.getGender();
            this.phone = user.getPhone();
            this.avatar = user.getAvatar();
            this.status = user.getStatus().ordinal();
        }
        if(classId != null) {
            this.classId = classId;
        }
    }
}