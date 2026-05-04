package com.edu.service;

import com.edu.domain.ActivityRecord;
import com.edu.domain.ActivityStatus;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ImportResult;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ActivityRecordRepository;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityImportValidator {
   private final DeepSeekService deepSeekService;
    private final ActivityRecordRepository activityRecordRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;

    /**
     * 获取学习时长解析阶段字段映射
     */
    public List<FieldMapping> getStudyParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 学生（必填）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);

        // 日期（必填）
        FieldMapping activityDate = new FieldMapping();
        activityDate.setTargetField("activityDate");
        activityDate.setFieldDescription("活动日期，格式：yyyy-MM-dd");
        activityDate.setPossibleNames(Arrays.asList("日期", "活动日期", "Date", "学习日期"));
        activityDate.setRequired(true);
        activityDate.setDataType("string");
        mappings.add(activityDate);

        // 学习时长（必填）
        FieldMapping studyDuration = new FieldMapping();
        studyDuration.setTargetField("studyDuration");
        studyDuration.setFieldDescription("学习时长（分钟）");
        studyDuration.setPossibleNames(Arrays.asList("学习时长", "时长", "分钟", "Study Duration", "Duration"));
        studyDuration.setRequired(true);
        studyDuration.setDataType("number");
        mappings.add(studyDuration);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("描述");
        description.setPossibleNames(Arrays.asList("描述", "备注", "说明", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        // 课程名称（可选）
        FieldMapping courseName = new FieldMapping();
        courseName.setTargetField("courseName");
        courseName.setFieldDescription("课程名称");
        courseName.setPossibleNames(Arrays.asList("课程", "课程名称", "Course"));
        courseName.setRequired(false);
        courseName.setDataType("string");
        mappings.add(courseName);

        // 知识点名称（可选）
        FieldMapping knowledgePointName = new FieldMapping();
        knowledgePointName.setTargetField("knowledgePointName");
        knowledgePointName.setFieldDescription("知识点名称");
        knowledgePointName.setPossibleNames(Arrays.asList("知识点", "知识点名称", "KnowledgePoint"));
        knowledgePointName.setRequired(false);
        knowledgePointName.setDataType("string");
        mappings.add(knowledgePointName);

        return mappings;
    }

    /**
     * 获取资源访问解析阶段字段映射
     */
    public List<FieldMapping> getResourceParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 学生（必填）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);

        // 日期（必填）
        FieldMapping activityDate = new FieldMapping();
        activityDate.setTargetField("activityDate");
        activityDate.setFieldDescription("活动日期，格式：yyyy-MM-dd");
        activityDate.setPossibleNames(Arrays.asList("日期", "活动日期", "Date", "访问日期"));
        activityDate.setRequired(true);
        activityDate.setDataType("string");
        mappings.add(activityDate);

        // 访问次数（必填）
        FieldMapping resourceAccessCount = new FieldMapping();
        resourceAccessCount.setTargetField("resourceAccessCount");
        resourceAccessCount.setFieldDescription("资源访问次数");
        resourceAccessCount.setPossibleNames(Arrays.asList("访问次数", "次数", "Resource Access Count", "Count"));
        resourceAccessCount.setRequired(true);
        resourceAccessCount.setDataType("number");
        mappings.add(resourceAccessCount);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("描述");
        description.setPossibleNames(Arrays.asList("描述", "备注", "说明", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        // 资源名称（可选）
        FieldMapping resourceName = new FieldMapping();
        resourceName.setTargetField("resourceName");
        resourceName.setFieldDescription("资源名称");
        resourceName.setPossibleNames(Arrays.asList("资源", "资源名称", "Resource"));
        resourceName.setRequired(false);
        resourceName.setDataType("string");
        mappings.add(resourceName);

        return mappings;
    }

    /**
     * 获取活动导入阶段字段映射（用于验证前端传来的数据）
     */
    public List<FieldMapping> getActivityImportFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("studentName", "学生"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);

        FieldMapping activityDate = new FieldMapping();
        activityDate.setTargetField("activityDate");
        activityDate.setFieldDescription("活动日期");
        activityDate.setPossibleNames(Arrays.asList("activityDate", "日期"));
        activityDate.setRequired(true);
        activityDate.setDataType("string");
        mappings.add(activityDate);

        FieldMapping studyDuration = new FieldMapping();
        studyDuration.setTargetField("studyDuration");
        studyDuration.setFieldDescription("学习时长");
        studyDuration.setPossibleNames(Arrays.asList("studyDuration", "学习时长"));
        studyDuration.setRequired(false);
        studyDuration.setDataType("number");
        mappings.add(studyDuration);

        FieldMapping resourceAccessCount = new FieldMapping();
        resourceAccessCount.setTargetField("resourceAccessCount");
        resourceAccessCount.setFieldDescription("资源访问次数");
        resourceAccessCount.setPossibleNames(Arrays.asList("resourceAccessCount", "访问次数"));
        resourceAccessCount.setRequired(false);
        resourceAccessCount.setDataType("number");
        mappings.add(resourceAccessCount);

        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("描述");
        description.setPossibleNames(Arrays.asList("description", "描述"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        return mappings;
    }

    /**
     * AI解析学习时长文件
     */
    public ParseResult parseAndConvertStudyFile(String fileContent, String fileName) {
        List<FieldMapping> mappings = getStudyParseFieldMappings();
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "study", mappings);
        
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            convertParseResultToIds(result);
        }
        return result;
    }

    /**
     * AI解析资源访问文件
     */
    public ParseResult parseAndConvertResourceFile(String fileContent, String fileName) {
        List<FieldMapping> mappings = getResourceParseFieldMappings();
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "resource", mappings);
        
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            convertParseResultToIds(result);
        }
        return result;
    }

    /**
     * 将解析结果中的名称转换为ID
     */
    private void convertParseResultToIds(ParseResult result) {
        List<Map<String, Object>> data = result.getData();
        
        for (Map<String, Object> row : data) {
            // 学生名称 -> studentId
            String studentIdentifier = (String) row.get("studentName");
            if (studentIdentifier != null && !studentIdentifier.isEmpty()) {
                Student student = findStudentByIdentifier(studentIdentifier);
                if (student != null) {
                    row.put("studentId", student.getId());
                } else {
                    row.put("studentId", null);
                    row.put("_error_studentName", "学生不存在: " + studentIdentifier);
                }
            }
            
            // 转换日期格式
        String dateStr = (String) row.get("activityDate");
        if (dateStr != null) {
            try {
                // 先去除可能的 T 和时区信息
                String cleanedDate = dateStr;
                if (cleanedDate.contains("T")) {
                    cleanedDate = cleanedDate.split("T")[0];
                }
                if (cleanedDate.contains("+")) {
                    cleanedDate = cleanedDate.split("\\+")[0];
                }
                LocalDate date = parseDate(cleanedDate);
                row.put("activityDate", date.toString());
            } catch (Exception e) {
                log.warn("日期格式转换失败: {}, 错误: {}", dateStr, e.getMessage());
                row.put("_error_activityDate", "日期格式无效: " + dateStr);
                row.put("activityDate", null);
            }
        }
    }
    }

    /**
     * 确认导入活动数据
     */
    @Transactional
    public ImportResult insertActivityData(ActivityStatus type, List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getActivityImportFieldMappings();
        
        // 1. 验证数据
        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            return ImportResult.builder()
                .success(false)
                .errorMessage(buildErrorMessage(errors))
                .build();
        }

        // 2. 批量导入
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        List<String> errorDetails = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            try {
                ActivityRecord record = createOrUpdateActivityRecord(type, row);
                if (record != null) {
                    successCount++;
                }
                log.info("成功导入{}数据 - 第{}行，学生：{}", 
                    type == ActivityStatus.STUDY ? "学习时长" : "资源访问",
                    i + 1, row.get("studentName"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("第%d行 - 学生：%s，原因：%s",
                    i + 1, row.get("studentName"), e.getMessage());
                log.error(errorMsg);
                errorDetails.add(errorMsg);
            }
        }

        boolean allSuccess = failCount == 0;
        String message = String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount);
        if (!allSuccess) {
            message += "，失败详情：" + String.join("; ", errorDetails);
        }

        return ImportResult.builder()
            .success(allSuccess)
            .successCount(successCount)
            .failCount(failCount)
            .errors(errorDetails)
            .message(message)
            .build();
    }

    /**
     * 创建或更新活动记录
     */
    private ActivityRecord createOrUpdateActivityRecord(ActivityStatus type, Map<String, Object> row) {

         String studentIdentifier = (String) row.get("studentName");
         Student student=null;
            if (studentIdentifier != null && !studentIdentifier.isEmpty()) {
                student = findStudentByIdentifier(studentIdentifier);
                if (student == null) {
                    throw new RuntimeException("学生不存在: " + studentIdentifier);
                }
            }else {
                throw new RuntimeException("学生不能为空");
            }

             // 获取日期，如果为空则抛出异常
    String dateStr = (String) row.get("activityDate");
    if (dateStr == null || dateStr.isEmpty()) {
        throw new RuntimeException("活动日期不能为空");
    }

         LocalDate activityDate = parseDate(dateStr);
    String description = (String) row.get("description");


        // 查找是否已存在当天的记录
        Optional<ActivityRecord> existingOptional = activityRecordRepository
            .findByStudentAndTypeAndActivityDateBetween(student, type, activityDate.atTime(0, 0, 0), activityDate.atTime(23, 59, 59));

          ActivityRecord record;
        Integer studyDuration = 0;
        Integer resourceAccessCount = 0;
         if (existingOptional.isPresent()) {
        record = existingOptional.get();
        // 更新已有的记录
        if (type == ActivityStatus.STUDY) {
            // 获取原有学习时长，默认为0
            Integer existingDuration = record.getStudyDuration() != null ? record.getStudyDuration() : 0;
            // 获取新增学习时长，默认为0
            Integer newDuration = row.get("studyDuration") != null ? ((Number) row.get("studyDuration")).intValue() : 0;
            studyDuration = existingDuration + newDuration;
            record.setStudyDuration(studyDuration);
            log.debug("累加学习时长: {} + {} = {}", existingDuration, newDuration, studyDuration);
        } else {
            // 获取原有访问次数，默认为0
            Integer existingCount = record.getResourceAccessCount() != null ? record.getResourceAccessCount() : 0;
            // 获取新增访问次数，默认为0
            Integer newCount = row.get("resourceAccessCount") != null ? ((Number) row.get("resourceAccessCount")).intValue() : 0;
            resourceAccessCount = existingCount + newCount;
            record.setResourceAccessCount(resourceAccessCount);
            log.debug("累加访问次数: {} + {} = {}", existingCount, newCount, resourceAccessCount);
        }
        if (description != null && !description.isEmpty()) {
            record.setDescription(description);
        }
    } else {
        // 创建新记录
        record = ActivityRecord.builder()
            .student(student)
            .type(type)
            .activityDate(activityDate.atTime(0, 0, 0))
            .description(description)
            .studyDuration(0)
            .resourceAccessCount(0)
            .build();

        if (type == ActivityStatus.STUDY) {
            studyDuration = row.get("studyDuration") != null ? ((Number) row.get("studyDuration")).intValue() : 0;
            record.setStudyDuration(studyDuration);
            log.debug("设置学习时长: {}", studyDuration);
        } else {
            resourceAccessCount = row.get("resourceAccessCount") != null ? ((Number) row.get("resourceAccessCount")).intValue() : 0;
            record.setResourceAccessCount(resourceAccessCount);
            log.debug("设置访问次数: {}", resourceAccessCount);
        }
    }

    // 计算活动得分
    // 规则：学习时长 10分钟 = 1分，访问次数 1次 = 1分
    int score = 0;
    if (type == ActivityStatus.STUDY) {
        // 学习时长：10分钟得1分，向下取整
        studyDuration = record.getStudyDuration() != null ? record.getStudyDuration() : 0;
        score = studyDuration / 10;
        log.debug("计算学习得分: 时长={}分钟, 得分={}", studyDuration, score);
    } else {
        // 访问次数：1次得1分
        resourceAccessCount = record.getResourceAccessCount() != null ? record.getResourceAccessCount() : 0;
        score = resourceAccessCount;
        log.debug("计算访问得分: 次数={}, 得分={}", resourceAccessCount, score);
    }
    
 // 设置活动得分（限制最大100分）
    int finalScore = Math.min(score, 100);
    record.setActivityScore(BigDecimal.valueOf(finalScore));
    log.debug("最终活动得分: {}", finalScore);
    
    // 设置互动次数（可选，默认为0）
    if (record.getInteractionCount() == null) {
        record.setInteractionCount(0);
    }

    return activityRecordRepository.save(record);
    }

    /**
     * 通过姓名或学号查找学生
     */
    private Student findStudentByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        
        String trimmedIdentifier = identifier.trim();
        
        Optional<Student> studentByNo = studentRepository.findByStudentNo(trimmedIdentifier);
        if (studentByNo.isPresent()) {
            return studentByNo.get();
        }
        
        Optional<User> userByUsername = userRepository.findByUsername(trimmedIdentifier);
        if (userByUsername.isPresent()) {
            User user = userByUsername.get();
            if (user.getRole() == com.edu.domain.Role.STUDENT) {
                return studentRepository.findByUser(user).orElse(null);
            }
        }
        
        List<User> usersByName = userRepository.findByName(trimmedIdentifier);
        for (User user : usersByName) {
            if (user.getRole() == com.edu.domain.Role.STUDENT) {
                Optional<Student> student = studentRepository.findByUser(user);
                if (student.isPresent()) return student.get();
            }
        }
        
        return null;
    }

    /**
     * 解析日期
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) throw new RuntimeException("日期不能为空");
        
       // 先处理 ISO 格式的日期时间字符串
    DateTimeFormatter[] formatters = {
        // ISO 格式：2026-05-02T00:00:00
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        // 标准格式
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
        // 带时间的格式
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
    };
    
    for (DateTimeFormatter formatter : formatters) {
        try {
            // 如果是日期时间格式，先解析为 LocalDateTime，再转为 LocalDate
            if (formatter.equals(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")) ||
                formatter.equals(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"))) {
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, formatter);
                return dateTime.toLocalDate();
            } else {
                return LocalDate.parse(dateStr, formatter);
            }
        } catch (DateTimeParseException e) {
            // 继续尝试
        }
    }
    
    // 处理 Excel 数字日期格式
    try {
        long excelDateNum = Long.parseLong(dateStr);
        LocalDate excelDate = LocalDate.of(1900, 1, 1).plusDays(excelDateNum - 2);
        return excelDate;
    } catch (NumberFormatException e) {
        // 不是数字格式
    }
    
    throw new RuntimeException("日期格式无效: " + dateStr + 
        "，请使用 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 格式");
    }

    private String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }
}
