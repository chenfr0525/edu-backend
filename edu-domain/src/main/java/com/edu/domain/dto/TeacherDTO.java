package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.edu.domain.Teacher;
import com.edu.domain.User;
import com.edu.domain.UserStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDTO {
    private Long id;
    private String teacherNo;   // 教师号
    private String name;        // 姓名
    private String username;    // 用户名
    private String role;
    private String department;   // 部门
    private String title;        // 职称
    private String office;       // 办公室
    private String gender;       // 性别
    private String email;       // 邮箱
    private String phone;       // 手机号
    private String avatar;      // 头像
    private UserStatus status;     // 状态
    private LocalDate joinDate;  // 加入时间

      public TeacherDTO(Teacher teacher, User user) {
        if (teacher != null) {
            this.id = teacher.getId();
            this.teacherNo = teacher.getTeacherNo();
            this.name = user.getName();
            this.username = user.getUsername();
            this.department = teacher.getDepartment();
            this.title = teacher.getTitle();
            this.office = teacher.getOffice();
            this.joinDate = teacher.getJoinDate() != null ? teacher.getJoinDate().toLocalDate() : null;
            this.email = user.getEmail();
            this.gender = user.getGender();
            this.phone = user.getPhone();
            this.avatar = user.getAvatar();
            this.status = user.getStatus();
            this.role=user.getRole().toString();
        }
    }

}
