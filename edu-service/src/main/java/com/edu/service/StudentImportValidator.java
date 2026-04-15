package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.edu.domain.ClassInfo;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentImportValidator {

   
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ClassRepository classInfoRepository;
  /**
     * 验证导入的数据
     */
    public List<ValidationError> validateStudentData(List<Map<String, Object>> dataList, 
                                                      List<FieldMapping> mappings) {
        List<ValidationError> errors = new ArrayList<>();
        Set<String> studentNos = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        
        for (int i = 0; i < dataList.size(); i++) {
            Map<String, Object> row = dataList.get(i);
            
            for (FieldMapping mapping : mappings) {
                Object value = row.get(mapping.getTargetField());
                
                // 1. 必填校验
                if (mapping.isRequired() && (value == null || value.toString().trim().isEmpty())) {
                    errors.add(new ValidationError(i, mapping.getTargetField(), 
                        String.format("第%d行：必填字段【%s】不能为空", i + 1, mapping.getFieldDescription()),
                        "required"));
                    continue;
                }
                
                if (value == null) continue;
                
                String strValue = value.toString().trim();
                
                // 2. 唯一性校验（学号）
                if ("studentNo".equals(mapping.getTargetField()) && mapping.isUnique()) {
                    if (studentNos.contains(strValue)) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：学号【%s】在文件中重复", i + 1, strValue),
                            "unique"));
                    } else {
                        // 检查数据库是否已存在
                        if (studentRepository.existsByStudentNo(strValue)) {
                            errors.add(new ValidationError(i, mapping.getTargetField(),
                                String.format("第%d行：学号【%s】已存在于系统中", i + 1, strValue),
                                "unique"));
                        } else {
                            studentNos.add(strValue);
                        }
                    }
                }
                
                // 3. 唯一性校验（用户名）
                if ("username".equals(mapping.getTargetField()) && mapping.isUnique()) {
                    if (usernames.contains(strValue)) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：用户名【%s】在文件中重复", i + 1, strValue),
                            "unique"));
                    } else {
                        if (userRepository.existsByUsername(strValue)) {
                            errors.add(new ValidationError(i, mapping.getTargetField(),
                                String.format("第%d行：用户名【%s】已存在于系统中", i + 1, strValue),
                                "unique"));
                        } else {
                            usernames.add(strValue);
                        }
                    }
                }
                
                // 4. 班级存在性校验
                if ("classname".equals(mapping.getTargetField()) && mapping.isNeedExist()) {
                    Optional<ClassInfo> classInfo = classInfoRepository.findByName(strValue);
                    if (!classInfo.isPresent()) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：班级【%s】不存在，请先创建班级", i + 1, strValue),
                            "exist"));
                    }
                }
                
                // 5. 性别校验
                if ("gender".equals(mapping.getTargetField())) {
                    if (!"男".equals(strValue) && !"女".equals(strValue)) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：性别【%s】无效，只能是男或女", i + 1, strValue),
                            "format"));
                    }
                }
                
                // 6. 邮箱格式校验
                if ("email".equals(mapping.getTargetField()) && !strValue.isEmpty()) {
                    String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
                    if (!Pattern.matches(emailRegex, strValue)) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：邮箱格式不正确", i + 1),
                            "format"));
                    }
                }
                
                // 7. 手机号格式校验
                if ("phone".equals(mapping.getTargetField()) && !strValue.isEmpty()) {
                    String phoneRegex = "^1[3-9]\\d{9}$";
                    if (!Pattern.matches(phoneRegex, strValue)) {
                        errors.add(new ValidationError(i, mapping.getTargetField(),
                            String.format("第%d行：手机号格式不正确", i + 1),
                            "format"));
                    }
                }
            }
        }
        
        return errors;
    }
    
    /**
     * 处理默认值
     */
    public void applyDefaultValues(List<Map<String, Object>> dataList, List<FieldMapping> mappings) {
        for (Map<String, Object> row : dataList) {
            for (FieldMapping mapping : mappings) {
                if (mapping.getDefaultValue() != null) {
                    Object value = row.get(mapping.getTargetField());
                    if (value == null || value.toString().trim().isEmpty()) {
                        row.put(mapping.getTargetField(), mapping.getDefaultValue());
                    }
                }
            }
        }
    }
    
    /**
     * 自动填充用户名（如果未提供，使用学号）
     */
    public void autoFillUsername(List<Map<String, Object>> dataList) {
        for (Map<String, Object> row : dataList) {
            String username = (String) row.get("username");
            String studentNo = (String) row.get("studentNo");
            
            if ((username == null || username.isEmpty()) && studentNo != null) {
                row.put("username", studentNo);
            }
        }
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
