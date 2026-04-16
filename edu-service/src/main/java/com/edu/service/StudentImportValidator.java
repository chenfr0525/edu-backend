package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.ClassInfo;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.UserStatus;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentImportValidator {

    private final DeepSeekService deepSeekService;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /**
     * 确认导入学生数据
     */
    @Transactional
    public String insertStudentData(List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getStudentFieldMappings();

        List<ValidationError> errors = deepSeekService.validateData(data,mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors) {
                sb.append(error.getErrorMessage()).append("\n");
            }
            log.error("数据验证失败：{}", sb.toString());
            return sb.toString();
        }
             // 2. 批量插入
                int successCount = 0;
                int failCount = 0;
                StringBuilder resultMsg = new StringBuilder();
            for (Map<String, Object> row : data) {
            try {
                insertSingleStudent(row);
                successCount++;
                log.info("成功插入学生：{}", row.get("name"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("插入失败 - 学号：%s，姓名：%s，原因：%s",
                    row.get("studentNo"), row.get("name"), e.getMessage());
                log.error(errorMsg);
                resultMsg.append(errorMsg).append("\n");
            }
            }
           if(failCount > 0){
        String summary = String.format("插入完成！成功：%d条，失败：%d条", successCount, failCount);
        log.info(summary);
    return resultMsg.toString();}
        return "数据导入成功";
    }

     /**
     * 插入单条学生数据
     */
    private void insertSingleStudent(Map<String, Object> row) {
        String studentNo = (String) row.get("studentNo");
        String name = (String) row.get("name");
        String username = (String) row.get("username");
        String classname = (String) row.get("classname");
        String grade = (String) row.get("grade");
        String gender = (String) row.get("gender");
        String email = (String) row.get("email");
        String phone = (String) row.get("phone");
        
        // 1. 检查学号是否已存在
        if (studentRepository.existsByStudentNo(studentNo)) {
            throw new RuntimeException("学号 " + studentNo + " 已存在");
        }
        
        // 2. 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名 " + username + " 已存在");
        }
        
        // 3. 根据班级名称查找班级ID
        ClassInfo classInfo = classRepository.findByName(classname)
            .orElseThrow(() -> new RuntimeException("班级 " + classname + " 不存在"));
        
        // 4. 创建 User 对象（登录账号）
        User user = User.builder()
            .username(username)
            .password(passwordEncoder.encode("123456"))  // 默认密码 123456
            .name(name)
            .email(email != null ? email : "")
            .gender(gender)
            .phone(phone != null ? phone : "")
            .status(UserStatus.ACTIVE)
            .role(Role.STUDENT)  // 学生角色
            .build();
        user = userRepository.save(user);
        
        // 5. 创建 Student 对象
        Student student = Student.builder()
            .studentNo(studentNo)
            .user(user)  // 关联 User ID
            .classInfo(classInfo)
            .grade(grade != null ? grade : "大一")
            .build();
        student = studentRepository.save(student);
        
        log.info("学生插入成功 - 学号：{}，姓名：{}，用户名：{}，班级：{}", 
            studentNo, name, username, classname);
    }

     public List<FieldMapping> getStudentFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        // 学号
        FieldMapping studentNo = new FieldMapping();
        studentNo.setTargetField("studentNo");
        studentNo.setFieldDescription("学生学号，唯一标识");
         studentNo.setPossibleNames(Arrays.asList("学号", "学生编号", "编号", "ID", "Student ID", "Student No", "SID", "student_id"));
        studentNo.setRequired(true);
        studentNo.setDataType("string");
        studentNo.setUnique(true);
        mappings.add(studentNo);
        
        // 姓名
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("学生姓名");
         name.setPossibleNames(Arrays.asList("姓名", "学生姓名", "名字", "Name", "Student Name", "Full Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);
        
        // 用户名
        FieldMapping username = new FieldMapping();
        username.setTargetField("username");
        username.setFieldDescription("用户名，如未提供可使用学号");
         username.setPossibleNames(Arrays.asList("用户名", "账号", "Username", "User Name"));
        username.setRequired(true);
        username.setDataType("string");
        username.setUnique(true);
        mappings.add(username);
        
        // 班级名称
        FieldMapping classname = new FieldMapping();
        classname.setTargetField("classname");
        classname.setFieldDescription("班级名称");
         classname.setPossibleNames(Arrays.asList("班级", "所属班级", "班别", "Class", "ClassName"));
        classname.setRequired(true);
        classname.setDataType("string");
        classname.setNeedExist(true);
        mappings.add(classname);
        
        // 年级
        FieldMapping grade = new FieldMapping();
        grade.setTargetField("grade");
        grade.setFieldDescription("年级");
        grade.setPossibleNames(Arrays.asList("年级","届" ,"Grade", "Year", "学年"));
        grade.setRequired(false);
        grade.setDataType("string");
        mappings.add(grade);
        
        // 邮箱
        FieldMapping email = new FieldMapping();
        email.setTargetField("email");
        email.setFieldDescription("电子邮箱");
        email.setPossibleNames(Arrays.asList("邮箱", "Email", "电子邮箱", "邮件"));
        email.setRequired(false);
        email.setDataType("string");
         email.setRegex("^[A-Za-z0-9+_.-]+@(.+)$");
        mappings.add(email);
        
        // 性别
        FieldMapping gender = new FieldMapping();
        gender.setTargetField("gender");
        gender.setFieldDescription("性别");
         gender.setPossibleNames(Arrays.asList("性别", "Gender", "Sex"));
        gender.setRequired(true);
        gender.setDataType("string");
        mappings.add(gender);
        
        // 电话
        FieldMapping phone = new FieldMapping();
        phone.setTargetField("phone");
        phone.setFieldDescription("联系电话");
        phone.setPossibleNames(Arrays.asList("电话", "手机", "Phone", "Mobile", "联系电话"));
        phone.setRequired(false);
        phone.setDataType("string");
        phone.setRegex("^1[3-9]\\d{9}$");
        mappings.add(phone);
        
        return mappings;
    }


}
