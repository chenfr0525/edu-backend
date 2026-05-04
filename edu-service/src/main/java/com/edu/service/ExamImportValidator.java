package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Teacher;
import com.edu.domain.User;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ImportResult;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ClassRepository;
import com.edu.repository.CourseRepository;
import com.edu.repository.EnrollmentRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.KnowledgePointRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.TeacherRepository;
import com.edu.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamImportValidator {

    private final DeepSeekService deepSeekService;
    private final ExamRepository examRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    
    /**
     * 确认导入考试数据
     */
    @Transactional
    public ImportResult insertExamData(List<Map<String, Object>> data, User user) {
        List<FieldMapping> mappings = getExamImportFieldMappings();
        Long teacherId = teacherRepository.findByUser(user).get().getId();
        
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
        List<String> errorDetails = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            try {
                insertSingleExam(row, teacherId);
                successCount++;
                log.info("成功导入考试：{}", row.get("name"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("第%d行 - 考试名称：%s，原因：%s",
                    i + 1, row.get("name"), e.getMessage());
                log.error(errorMsg);
                errorDetails.add(errorMsg);
                throw new RuntimeException("导入失败，已回滚所有数据：" + errorMsg);
            }
        }
 boolean allSuccess = failCount == 0;
        String message = String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount);
        if (!allSuccess) {
            message += "，失败详情：" + String.join("\n", errorDetails);
        }

        return ImportResult.builder()
            .success(allSuccess)
            .successCount(successCount)
            .failCount(failCount)
            .errors(errorDetails)
            .message(message)
            .build();
    }

    private String buildErrorMessage(List<ValidationError> errors) {
    StringBuilder sb = new StringBuilder();
    for (ValidationError error : errors) {
        sb.append(error.getErrorMessage()).append("\n");
    }
    return sb.toString();
}

    /**
     * 插入单条考试数据
     */
    private void insertSingleExam(Map<String, Object> row,Long teacherId) {
        String name = (String) row.get("name");
        String type = (String) row.get("type");
        Long classId = row.get("classId") != null ? ((Number) row.get("classId")).longValue() : null;
        Long courseId = ((Number) row.get("courseId")).longValue();
        String examDateStr = (String) row.get("examDate");

        // 可选字段
        Integer fullScore = row.get("fullScore") != null ? ((Number) row.get("fullScore")).intValue() : 100;
        Integer passScore = row.get("passScore") != null ? ((Number) row.get("passScore")).intValue() : (int)(fullScore * 0.6);
        String description = (String) row.get("description");

        // 知识点ID列表（AI解析后返回的）
        List<Long> knowledgePointIds = null;
        if (row.get("knowledgePointIds") != null) {
            if (row.get("knowledgePointIds") instanceof List) {
                knowledgePointIds = (List<Long>) row.get("knowledgePointIds");
            } else if (row.get("knowledgePointIds") instanceof String) {
                String kpStr = (String) row.get("knowledgePointIds");
                try {
                    knowledgePointIds = JSON.parseArray(kpStr, Long.class);
                } catch (Exception e) {
                    log.warn("解析知识点ID列表失败: {}", kpStr);
                }
            }
        }

        // 1. 查找课程
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程ID " + courseId + " 不存在"));

        // 2. 查找班级（可选）
        ClassInfo classInfo = null;
        if (classId != null) {
            classInfo = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("班级ID " + classId + " 不存在"));
        }

        // 3. 检查同名考试是否已存在
        if (classInfo != null && examRepository.existsByClassInfoAndCourseAndName(classInfo, course, name)) {
            throw new RuntimeException("考试 " + name + " 在课程 " + course.getName() + " 和班级 " + classInfo.getName() + " 中已存在");
        };

        // 4. 解析考试日期
        LocalDateTime examDateTime = parseDateTime(examDateStr);
    
        // 5. 确定考试状态
        ExamStatus status = examDateTime.isBefore(LocalDateTime.now()) ? ExamStatus.ONGOING : ExamStatus.UPCOMING;


        // 7. 创建 Exam 对象
        Exam exam = Exam.builder()
            .name(name)
            .type(ExamStatus.valueOf(type))
            .classInfo(classInfo)
            .course(course)
            .examDate(examDateTime)
            .fullScore(fullScore)
            .passScore(passScore)
            .description(description)
            .status(status)
            .knowledgePointIds(knowledgePointIds)
            .build();

      examRepository.save(exam);
        log.info("考试导入成功 - 名称：{}，课程：{}，班级：{}，日期：{}", 
            name, course.getName(), classInfo != null ? classInfo.getName() : "无", examDateTime);
    }

    /**
     * 获取考试字段映射配置
     */
    public List<FieldMapping> getExamParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 考试名称（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("考试名称");
        name.setPossibleNames(Arrays.asList("考试名称", "名称", "考试名", "Exam Name", "Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);

        // 考试类型（必填）
        FieldMapping type = new FieldMapping();
        type.setTargetField("type");
        type.setFieldDescription("考试类型：模拟考/单元测试/月考/期中考试/期末考试");
        type.setPossibleNames(Arrays.asList("类型", "考试类型", "Type", "Exam Type"));
        type.setRequired(true);
        type.setDataType("string");
        type.setAllowedValues(Arrays.asList("MOCK", "UNIT", "MONTHLY", "MIDTERM", "FINAL"));
        mappings.add(type);

        // 课程名称（必填，需存在）
        FieldMapping coursename = new FieldMapping();
        coursename.setTargetField("coursename");
        coursename.setFieldDescription("课程名称");
        coursename.setPossibleNames(Arrays.asList("课程", "课程名称", "科目", "Course", "CourseName"));
        coursename.setRequired(true);
        coursename.setDataType("string");
        coursename.setNeedExist(true);
        mappings.add(coursename);

        // 考试日期（必填）
        FieldMapping examDate = new FieldMapping();
        examDate.setTargetField("examDate");
        examDate.setFieldDescription("考试日期，格式：yyyy-MM-dd");
        examDate.setPossibleNames(Arrays.asList("考试日期", "日期", "Exam Date", "Date"));
        examDate.setRequired(true);
        examDate.setDataType("string");
        mappings.add(examDate);


        // 班级名称（可选）
        FieldMapping classname = new FieldMapping();
        classname.setTargetField("classname");
        classname.setFieldDescription("班级名称");
        classname.setPossibleNames(Arrays.asList("班级", "所属班级", "班别", "Class", "ClassName"));
        classname.setRequired(false);
        classname.setDataType("string");
        mappings.add(classname);

        // 总分（可选，默认100）
        FieldMapping fullScore = new FieldMapping();
        fullScore.setTargetField("fullScore");
        fullScore.setFieldDescription("总分");
        fullScore.setPossibleNames(Arrays.asList("总分", "满分", "Full Score", "Total Score"));
        fullScore.setRequired(false);
        fullScore.setDataType("number");
        mappings.add(fullScore);

        // 及格分（可选，默认满分的60%）
        FieldMapping passScore = new FieldMapping();
        passScore.setTargetField("passScore");
        passScore.setFieldDescription("及格分");
        passScore.setPossibleNames(Arrays.asList("及格分", "及格线", "Pass Score"));
        passScore.setRequired(false);
        passScore.setDataType("number");
        mappings.add(passScore);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("考试说明");
        description.setPossibleNames(Arrays.asList("说明", "备注", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

         // 知识点名称（可选）
        FieldMapping knowledgePointNames = new FieldMapping();
        knowledgePointNames.setTargetField("knowledgePointNames");
        knowledgePointNames.setFieldDescription("知识点名称，多个用逗号分隔");
        knowledgePointNames.setPossibleNames(Arrays.asList("知识点", "知识点名称", "KnowledgePoint"));
        knowledgePointNames.setRequired(false);
        knowledgePointNames.setDataType("string");
        mappings.add(knowledgePointNames);

        return mappings;
    }

     /**
     * 获取考试导入阶段字段映射（用于验证前端传来的数据）
     * 字段名：代码中使用的字段名（courseId, classId, knowledgePointIds）
     */
    public List<FieldMapping> getExamImportFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 考试名称（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("考试名称");
        name.setPossibleNames(Arrays.asList("name", "考试名称"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);

        // 考试类型（必填）
        FieldMapping type = new FieldMapping();
        type.setTargetField("type");
        type.setFieldDescription("考试类型");
        type.setPossibleNames(Arrays.asList("type", "考试类型"));
        type.setRequired(true);
        type.setDataType("string");
        type.setAllowedValues(Arrays.asList("MOCK", "UNIT", "MONTHLY", "MIDTERM", "FINAL"));
        mappings.add(type);

        // 课程ID（必填）
        FieldMapping courseId = new FieldMapping();
        courseId.setTargetField("courseId");
        courseId.setFieldDescription("课程ID");
        courseId.setPossibleNames(Arrays.asList("courseId", "课程ID"));
        courseId.setRequired(true);
        courseId.setDataType("number");
        courseId.setNeedExist(true);
        mappings.add(courseId);

        // 考试日期（必填）
        FieldMapping examDate = new FieldMapping();
        examDate.setTargetField("examDate");
        examDate.setFieldDescription("考试日期");
        examDate.setPossibleNames(Arrays.asList("examDate", "考试日期"));
        examDate.setRequired(true);
        examDate.setDataType("string");
        mappings.add(examDate);

        // 班级ID（可选）
        FieldMapping classId = new FieldMapping();
        classId.setTargetField("classId");
        classId.setFieldDescription("班级ID");
        classId.setPossibleNames(Arrays.asList("classId", "班级ID"));
        classId.setRequired(false);
        classId.setDataType("number");
        classId.setNeedExist(true);
        mappings.add(classId);

        // 知识点ID列表（可选）
        FieldMapping knowledgePointIds = new FieldMapping();
        knowledgePointIds.setTargetField("knowledgePointIds");
        knowledgePointIds.setFieldDescription("知识点ID列表");
        knowledgePointIds.setPossibleNames(Arrays.asList("knowledgePointIds", "知识点ID"));
        knowledgePointIds.setRequired(false);
        knowledgePointIds.setDataType("array");
        mappings.add(knowledgePointIds);

        // 总分（可选）
        FieldMapping fullScore = new FieldMapping();
        fullScore.setTargetField("fullScore");
        fullScore.setFieldDescription("总分");
        fullScore.setPossibleNames(Arrays.asList("fullScore", "总分"));
        fullScore.setRequired(false);
        fullScore.setDataType("number");
        mappings.add(fullScore);

        // 及格分（可选）
        FieldMapping passScore = new FieldMapping();
        passScore.setTargetField("passScore");
        passScore.setFieldDescription("及格分");
        passScore.setPossibleNames(Arrays.asList("passScore", "及格分"));
        passScore.setRequired(false);
        passScore.setDataType("number");
        mappings.add(passScore);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("考试说明");
        description.setPossibleNames(Arrays.asList("description", "描述"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        return mappings;
    }



  /**
 * AI解析考试文件后，自动将名称转换为ID
 */
public ParseResult parseAndConvertExamFile(String fileContent, String fileName, 
                                            Long currentUserId, String userRole) {
     // 1. 获取解析阶段的字段映射
        List<FieldMapping> mappings = getExamParseFieldMappings();
    
    // 2. AI解析文件
    ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "exam", mappings);
    
   // 3. 解析成功后，自动转换名称到ID
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            convertExamParseResultToIds(result, currentUserId, userRole);
        }
        
        return result;
}

 /**
     * 将考试解析结果中的名称转换为ID
     */
    private void convertExamParseResultToIds(ParseResult result, Long currentUserId, String userRole) {
        List<Map<String, Object>> data = result.getData();
        
        // 获取可见的课程列表
        List<Course> visibleCourses = getVisibleCourses(currentUserId, userRole);
        Map<String, Long> courseNameToIdMap = visibleCourses.stream()
            .collect(Collectors.toMap(
                Course::getName,
                Course::getId,
                (existing, replacement) -> existing
            ));
        
        // 获取可见的班级列表
        List<ClassInfo> visibleClasses = getVisibleClasses(currentUserId, userRole);
        Map<String, Long> classNameToIdMap = visibleClasses.stream()
            .collect(Collectors.toMap(
                ClassInfo::getName,
                ClassInfo::getId,
                (existing, replacement) -> existing
            ));
        
        // 获取所有知识点（按课程分组）
        Map<Long, Map<String, Long>> courseKpNameToIdMap = new HashMap<>();
        for (Course course : visibleCourses) {
            List<KnowledgePoint> kps = knowledgePointRepository.findByCourse(course);
            Map<String, Long> kpNameMap = kps.stream()
                .collect(Collectors.toMap(
                    KnowledgePoint::getName,
                    KnowledgePoint::getId,
                    (existing, replacement) -> existing
                ));
            courseKpNameToIdMap.put(course.getId(), kpNameMap);
        }
        
        for (Map<String, Object> row : data) {
            // 1. 课程名称 -> courseId
            String courseName = null;
            if (row.containsKey("courseName")) {
                courseName = (String) row.get("courseName");
            } else if (row.containsKey("coursename")) {
                courseName = (String) row.get("coursename");
            } else if (row.containsKey("course_name")) {
                courseName = (String) row.get("course_name");
            }
            if (courseName != null && !courseName.isEmpty()) {
                Long courseId = courseNameToIdMap.get(courseName);
                if (courseId != null) {
                    row.put("courseId", courseId);
                     // 移除原始字段
                    row.remove("courseName");
                    row.remove("coursename");
                    row.remove("course_name");
                } else {
                    row.put("courseId", null);
                    row.put("_error_courseName", "课程不存在: " + courseName);
                }
            }
            
            // 2. 班级名称 -> classId
             String className = null;
            if (row.containsKey("className")) {
                className = (String) row.get("className");
            } else if (row.containsKey("classname")) {
                className = (String) row.get("classname");
            } else if (row.containsKey("class_name")) {
                className = (String) row.get("class_name");
            }
            if (className != null && !className.isEmpty()) {
                Long classId = classNameToIdMap.get(className);
                if (classId != null) {
                    row.put("classId", classId);
                     // 移除原始字段
                    row.remove("className");
                    row.remove("classname");
                    row.remove("class_name");
                } else {
                    row.put("classId", null);
                    row.put("_error_className", "班级不存在: " + className);
                }
            }

             // 3. 考试类型转换：中文 -> 英文枚举值
            String typeName = (String) row.get("type");
            if (typeName != null && !typeName.isEmpty()) {
                String normalizedType = normalizeExamType(typeName);
                row.put("type", normalizedType);
            }
            
            // 4. 知识点名称 -> knowledgePointIds
            Long courseId = (Long) row.get("courseId");
            String knowledgePointNames = null;
            if (row.containsKey("knowledgePointNames")) {
                knowledgePointNames = (String) row.get("knowledgePointNames");
            } else if (row.containsKey("knowledgePointNames")) {
                knowledgePointNames = (String) row.get("knowledgePointNames");
            } else if (row.containsKey("knowledge_point_names")) {
                knowledgePointNames = (String) row.get("knowledge_point_names");
            }
            if (courseId != null && knowledgePointNames != null && !knowledgePointNames.isEmpty()) {
                Map<String, Long> kpNameMap = courseKpNameToIdMap.get(courseId);
                if (kpNameMap != null) {
                    String[] kpNameArray = knowledgePointNames.split("[,，、]");
                    List<Long> kpIds = new ArrayList<>();
                    List<String> notFoundKps = new ArrayList<>();
                    
                    for (String kpName : kpNameArray) {
                        String trimmed = kpName.trim();
                        Long kpId = kpNameMap.get(trimmed);
                        if (kpId != null) {
                            kpIds.add(kpId);
                        } else {
                            notFoundKps.add(trimmed);
                        }
                    }
                    
                    if (!kpIds.isEmpty()) {
                        row.put("knowledgePointIds", kpIds);
                    }
                    if (!notFoundKps.isEmpty()) {
                        row.put("_error_knowledgePointNames", "知识点不存在: " + String.join(", ", notFoundKps));
                    }
                }
            }
            
            // 4. 转换考试日期格式
            String examDate = (String) row.get("examDate");
            if (examDate != null) {
                try {
                    // 处理 Excel 数字日期格式
                    if (examDate.matches("\\d+")) {
                        long excelDateNum = Long.parseLong(examDate);
                        LocalDate excelDate = LocalDate.of(1900, 1, 1).plusDays(excelDateNum - 2);
                        row.put("examDate", excelDate.atStartOfDay().toString());
                    }
                } catch (Exception e) {
                    log.warn("日期格式转换失败: {}", examDate);
                }
            }
            
            // 5. 设置默认值
            if (row.get("fullScore") == null) {
                row.put("fullScore", 100);
            }
            if (row.get("passScore") == null) {
                Integer fullScore = (Integer) row.get("fullScore");
                row.put("passScore", fullScore != null ? (int)(fullScore * 0.6) : 60);
            }
        }
    }
   
     /**
     * 获取可见的课程列表
     */
    private List<Course> getVisibleCourses(Long userId, String userRole) {
        if ("ADMIN".equals(userRole)) {
            return courseRepository.findAll();
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            return courseRepository.findByTeacher(teacher);
        }
    }

     /**
     * 获取可见的班级列表
     */
    private List<ClassInfo> getVisibleClasses(Long userId, String userRole) {
        if ("ADMIN".equals(userRole)) {
            return classRepository.findAll();
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            return classRepository.findByTeacher(teacher);
        }
    }

    /**
     * 标准化考试类型
     */
    private String normalizeExamType(String type) {
        if (type == null) return "UNIT";

        String trimmed = type.trim();
        
        switch (trimmed) {
            case "模拟考": case "MOCK": return "MOCK";
            case "单元测试": case "UNIT": return "UNIT";
            case "月考": case "MONTHLY": return "MONTHLY";
            case "期中考试": case "MIDTERM": return "MIDTERM";
            case "期末考试": case "FINAL": return "FINAL";
            default:
                String upper = trimmed.toUpperCase();
                if (upper.equals("MOCK") || upper.equals("UNIT") || 
                    upper.equals("MONTHLY") || upper.equals("MIDTERM") || 
                    upper.equals("FINAL")) {
                    return upper;
                }
                return "UNIT";
        }
    }
   /**
 * 解析日期时间字符串
 */
private LocalDateTime parseDateTime(String dateStr) {
    if (dateStr == null) throw new RuntimeException("日期时间不能为空");
    
    DateTimeFormatter[] formatters = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,           // 2024-01-15T10:30:00
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), // 2024-01-15 10:30:00
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"), // 2024/01/15 10:30:00
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"), // 2024.01.15 10:30:00
        DateTimeFormatter.ISO_LOCAL_DATE,                // 2024-01-15 (缺时间默认为00:00:00)
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),       // 2024/01/15
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),       // 2024-01-15
        DateTimeFormatter.ofPattern("yyyy.MM.dd")        // 2024.01.15
    };
    
    for (DateTimeFormatter formatter : formatters) {
        try {
            // 如果解析的是 LocalDate，转换为当天开始的 LocalDateTime
            if (formatter.equals(DateTimeFormatter.ISO_LOCAL_DATE) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy/MM/dd")) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy.MM.dd"))) {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.atStartOfDay(); // 默认时间为 00:00:00
            }
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {

        }
    }
    
    throw new RuntimeException("日期时间格式无效: " + dateStr + 
        "，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
}
    /**
     * 解析时间字符串
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null) return null;
        
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }
        
        throw new RuntimeException("时间格式无效: " + timeStr + "，请使用 HH:mm:ss 格式");
    }
}