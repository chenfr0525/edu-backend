package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.ClassInfo;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.UserStatus;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ImportResult;
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
    private final AuthService authService;
    /**
     * 确认导入学生数据
     */
    @Transactional
    public ImportResult insertStudentData(List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getStudentImportFieldMappings();
        
        // 1. 验证数据
        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return ImportResult.builder()
                .success(false)
                .errorMessage(buildErrorMessage(errors))
                .errors(errors.stream()
                    .map(ValidationError::toString)
                    .collect(Collectors.toList()))
                .build();
        }
              // 2. 批量插入
        int successCount = 0;
        int failCount = 0;
        List<String> successDetails = new ArrayList<>();
        List<String> errorDetails = new ArrayList<>();
        List<Map<String, Object>> successData = new ArrayList<>();
        
       for (int i = 0; i < data.size(); i++) {
        Map<String, Object> row = data.get(i);
        try {
            insertSingleStudent(row);
            successCount++;
            successDetails.add(String.format("第%d行 - 学号：%s，姓名：%s 导入成功",
                i + 1, row.get("studentNo"), row.get("name")));
            successData.add(row);
            log.info("成功插入学生：{}", row.get("name"));
        } catch (Exception e) {
            failCount++;
            String errorMsg = String.format("第%d行 - 学号：%s，姓名：%s，原因：%s",
                i + 1, row.get("studentNo"), row.get("name"), e.getMessage());
            log.error(errorMsg);
            errorDetails.add(errorMsg);
        }
    }
        // 3. 返回结果（不管是否有失败，都返回详细信息）
    boolean allSuccess = failCount == 0;
    String message = String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount);

    if(!allSuccess){
        message += "，失败详情：";
        message += String.join("\n", errorDetails);
    }
    
    ImportResult.ImportResultBuilder builder = ImportResult.builder()
        .success(allSuccess)
        .successCount(successCount)
        .failCount(failCount)
        .message(message);
    
    return builder.build();
    }

        private String buildErrorMessage(List<ValidationError> errors) {
            StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors) {
                sb.append(error.getErrorMessage()).append("\n");
            }
            return sb.toString();
        }

     /**
     * 插入单条学生数据
     */
   private void insertSingleStudent(Map<String, Object> row) {
    User currentUser = authService.getUser();
    
    // ✅ 安全获取字符串值
    String studentNo = getStringValue(row.get("studentNo"));
    String name = getStringValue(row.get("name"));
    String username = getStringValue(row.get("username"));
    // ✅ 获取 classId（可能为 null）
    Long classId = null;
    Object classIdObj = row.get("classId");
    if (classIdObj != null) {
        if (classIdObj instanceof Number) {
            classId = ((Number) classIdObj).longValue();
        } else if (classIdObj instanceof String) {
            try {
                classId = Long.parseLong((String) classIdObj);
            } catch (NumberFormatException e) {
                // 不是数字，忽略
            }
        }
    }
    String classname = getStringValue(row.get("classname"));
    String grade = getStringValue(row.get("grade"));
    String gender = getStringValue(row.get("gender"));
    String email = getStringValue(row.get("email"));
    String phone = getStringValue(row.get("phone"));
    
    // 1. 检查学号是否已存在
    if (studentRepository.existsByStudentNo(studentNo)) {
        throw new RuntimeException("学号 " + studentNo + " 已存在");
    }
    
    // 2. 检查用户名是否已存在
    if (userRepository.existsByUsername(username)) {
        throw new RuntimeException("用户名 " + username + " 已存在");
    }
    
    // 3. 必填字段校验
    if (gender == null || gender.trim().isEmpty()) {
        throw new RuntimeException("性别不能为空");
    }

    ClassInfo classInfo = null;

    // 4. 查找班级：优先使用 classId，其次使用 classname
    if (classId != null) {
        // 优先按 ID 查找
        classInfo = classRepository.findById(classId).orElse(null);
        if (classInfo != null) {
            log.debug("通过 classId={} 找到班级: {}", classId, classInfo.getName());
        } else {
            log.warn("班级ID {} 不存在", classId);
        }
    }
    
    // 如果通过 ID 没找到，再按名称查找
    if (classInfo == null && classname != null && !classname.trim().isEmpty()) {
        if (currentUser.getRole() == Role.TEACHER) {
            // 教师只能使用自己管理的班级
            classInfo = classRepository.findByName(classname)
                .orElseThrow(() -> new RuntimeException("班级 " + classname + " 不存在，您无法创建新班级"));
        } else {
            // 管理员支持自动创建班级
            classInfo = classRepository.findByName(classname)
                .orElseGet(() -> {
                    log.info("班级 {} 不存在，自动创建", classname);
                    ClassInfo newClass = new ClassInfo();
                    newClass.setName(classname);
                    newClass.setGrade(grade != null ? grade : "大一");
                    return classRepository.save(newClass);
                });
        }
    }
    
    // 最终检查班级是否找到
    if (classInfo == null) {
        throw new RuntimeException("无法确定班级，请提供 classId 或 classname");
    }
    
    // 5. 创建 User 对象
    User user = User.builder()
        .username(username)
        .password(passwordEncoder.encode("123456"))
        .name(name)
        .email(email != null ? email : "")
        .gender(gender)
        .phone(phone != null ? phone : "")
        .status(UserStatus.ACTIVE)
        .role(Role.STUDENT)
        .build();
    user = userRepository.save(user);
    
    // 6. 创建 Student 对象
    Student student = Student.builder()
        .studentNo(studentNo)
        .user(user)
        .classInfo(classInfo)
        .build();
    student = studentRepository.save(student);
    
    log.info("学生插入成功 - 学号：{}，姓名：{}，用户名：{}，班级：{}", 
        studentNo, name, username, classInfo.getName());
}

// ✅ 辅助方法：安全转换为字符串
private String getStringValue(Object obj) {
    if (obj == null) {
        return null;
    }
    if (obj instanceof String) {
        return (String) obj;
    }
    if (obj instanceof Number) {
        return String.valueOf(obj);
    }
    return obj.toString();
}

/**
 * 用于导入验证阶段的字段映射（验证前端传来的数据）
 * 字段名：代码中使用的字段名，如 classId, studentNo
 */
public List<FieldMapping> getStudentImportFieldMappings() {
    List<FieldMapping> mappings = new ArrayList<>();
    
    // 学号（必填，唯一）
    FieldMapping studentNo = new FieldMapping();
    studentNo.setTargetField("studentNo");
    studentNo.setFieldDescription("学生学号");
    studentNo.setPossibleNames(Arrays.asList("studentNo", "学号"));
    studentNo.setRequired(true);
    studentNo.setDataType("string");
    studentNo.setUnique(true);
    mappings.add(studentNo);
    
    // 姓名（必填）
    FieldMapping name = new FieldMapping();
    name.setTargetField("name");
    name.setFieldDescription("学生姓名");
    name.setPossibleNames(Arrays.asList("name", "姓名"));
    name.setRequired(true);
    name.setDataType("string");
    mappings.add(name);
    
    // 用户名（必填）
    FieldMapping username = new FieldMapping();
    username.setTargetField("username");
    username.setFieldDescription("用户名");
    username.setPossibleNames(Arrays.asList("username", "用户名"));
    username.setRequired(true);
    username.setDataType("string");
    username.setUnique(true);
    mappings.add(username);
    
    // 班级ID（必填）- 导入时用的是 ID
    FieldMapping classId = new FieldMapping();
    classId.setTargetField("classId");
    classId.setFieldDescription("班级ID");
    classId.setPossibleNames(Arrays.asList("classId", "班级ID"));
    classId.setRequired(true);
    classId.setDataType("number");
    classId.setNeedExist(true);
    mappings.add(classId);
    
    // 性别（必填）
    FieldMapping gender = new FieldMapping();
    gender.setTargetField("gender");
    gender.setFieldDescription("性别");
    gender.setPossibleNames(Arrays.asList("gender", "性别"));
    gender.setRequired(true);
    gender.setDataType("string");
    mappings.add(gender);
    
    // 年级（可选）
    FieldMapping grade = new FieldMapping();
    grade.setTargetField("grade");
    grade.setFieldDescription("年级");
    grade.setPossibleNames(Arrays.asList("grade", "年级"));
    grade.setRequired(false);
    grade.setDataType("string");
    mappings.add(grade);
    
    // 邮箱（可选）
    FieldMapping email = new FieldMapping();
    email.setTargetField("email");
    email.setFieldDescription("电子邮箱");
    email.setPossibleNames(Arrays.asList("email", "邮箱"));
    email.setRequired(false);
    email.setDataType("string");
    mappings.add(email);
    
    // 电话（可选）
    FieldMapping phone = new FieldMapping();
    phone.setTargetField("phone");
    phone.setFieldDescription("联系电话");
    phone.setPossibleNames(Arrays.asList("phone", "电话"));
    phone.setRequired(false);
    phone.setDataType("string");
    mappings.add(phone);
    
    return mappings;
}
     public List<FieldMapping> getStudentFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
         // 学号（必填，唯一）
        FieldMapping studentNo = new FieldMapping();
        studentNo.setTargetField("studentNo");
        studentNo.setFieldDescription("学生学号，唯一标识");
        studentNo.setPossibleNames(Arrays.asList("学号", "学生编号", "编号", "ID", "Student ID", "Student No", "SID"));
        studentNo.setRequired(true);
        studentNo.setDataType("string");
        studentNo.setUnique(true);
        mappings.add(studentNo);
        
        // 姓名（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("学生真实姓名");
        name.setPossibleNames(Arrays.asList("姓名", "学生姓名", "名字", "Name", "Student Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);
        
        // 用户名（必填，唯一，默认学号）
        FieldMapping username = new FieldMapping();
        username.setTargetField("username");
        username.setFieldDescription("用户名，如未提供可使用学号");
        username.setPossibleNames(Arrays.asList("用户名", "账号", "Username"));
        username.setRequired(true);
        username.setDataType("string");
        username.setUnique(true);
        mappings.add(username);
        
         // 班级名称（必填，需存在）
        FieldMapping classname = new FieldMapping();
        classname.setTargetField("classname");
        classname.setFieldDescription("班级名称");
        classname.setPossibleNames(Arrays.asList("班级", "所属班级", "班别", "Class", "ClassName"));
        classname.setRequired(true);
        classname.setDataType("string");
        classname.setNeedExist(true);
        mappings.add(classname);
        
         // 年级（可选）
        FieldMapping grade = new FieldMapping();
        grade.setTargetField("grade");
        grade.setFieldDescription("年级");
        grade.setPossibleNames(Arrays.asList("年级", "Grade"));
        grade.setRequired(false);
        grade.setDataType("string");
        mappings.add(grade);
        
        // 性别（必填）
        FieldMapping gender = new FieldMapping();
        gender.setTargetField("gender");
        gender.setFieldDescription("性别");
        gender.setPossibleNames(Arrays.asList("性别", "Gender"));
        gender.setRequired(true);
        gender.setDataType("string");
        mappings.add(gender);
    
        // 邮箱（可选）
        FieldMapping email = new FieldMapping();
        email.setTargetField("email");
        email.setFieldDescription("电子邮箱");
        email.setPossibleNames(Arrays.asList("邮箱", "Email"));
        email.setRequired(false);
        email.setDataType("string");
        email.setRegex("^[A-Za-z0-9+_.-]+@(.+)$");
        mappings.add(email);
        
         // 电话（可选）
        FieldMapping phone = new FieldMapping();
        phone.setTargetField("phone");
        phone.setFieldDescription("联系电话");
        phone.setPossibleNames(Arrays.asList("电话", "手机", "Phone"));
        phone.setRequired(false);
        phone.setDataType("string");
        phone.setRegex("^1[3-9]\\d{9}$");
        mappings.add(phone);
        
        return mappings;
    }


}
