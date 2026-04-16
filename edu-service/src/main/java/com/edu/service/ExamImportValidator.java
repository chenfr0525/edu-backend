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
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ClassRepository;
import com.edu.repository.CourseRepository;
import com.edu.repository.EnrollmentRepository;
import com.edu.repository.ExamRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamImportValidator {

    private final DeepSeekService deepSeekService;
    private final ExamRepository examRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * 确认导入考试数据
     */
    @Transactional
    public String insertExamData(List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getExamFieldMappings();

        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors) {
                sb.append(error.getErrorMessage()).append("\n");
            }
            log.error("数据验证失败：{}", sb.toString());
            return sb.toString();
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder resultMsg = new StringBuilder();

        for (Map<String, Object> row : data) {
            try {
                insertSingleExam(row);
                successCount++;
                log.info("成功导入考试：{}", row.get("name"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("导入失败 - 考试名称：%s，原因：%s",
                    row.get("name"), e.getMessage());
                log.error(errorMsg);
                resultMsg.append(errorMsg).append("\n");
            }
        }

        if (failCount > 0) {
            String summary = String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount);
            log.info(summary);
            return resultMsg.toString();
        }
        return "数据导入成功";
    }

    /**
     * 插入单条考试数据
     */
    private void insertSingleExam(Map<String, Object> row) {
        String name = (String) row.get("name");
        String type = (String) row.get("type");
        String classname = (String) row.get("classname");
        String coursename = (String) row.get("coursename");
        String examDateStr = (String) row.get("examDate");
        String startTimeStr = (String) row.get("startTime");
        String endTimeStr = (String) row.get("endTime");
        Integer duration = (Integer) row.get("duration");
        Integer fullScore = row.get("fullScore") != null ? ((Number) row.get("fullScore")).intValue() : 100;
        Integer passScore = row.get("passScore") != null ? ((Number) row.get("passScore")).intValue() : (int)(fullScore * 0.6);
        String location = (String) row.get("location");
        String description = (String) row.get("description");

        // 1. 检查考试名称是否已存在（同一课程下）
        Course course = courseRepository.findByName(coursename)
            .orElseThrow(() -> new RuntimeException("课程 " + coursename + " 不存在"));

        ClassInfo classInfo = null;
        if (classname != null && !classname.isEmpty()) {
            classInfo = classRepository.findByName(classname)
                .orElseThrow(() -> new RuntimeException("班级 " + classname + " 不存在"));
        }

        // 检查同名考试是否已存在
        if (classInfo != null && examRepository.existsByClassInfoAndCourseAndName(classInfo, course,name )) {
            throw new RuntimeException("考试 " + name + " 在课程 " + coursename + " 和班级 " + classname + " 中已存在");
        }

        // 2. 解析日期和时间
        LocalDateTime examDate = parseDateTime(examDateStr);
        LocalDateTime startTime = startTimeStr != null ? parseDateTime(startTimeStr) : null;
        LocalDateTime endTime = endTimeStr != null ? parseDateTime(endTimeStr) : null;

        // 3. 确定考试类型（标准化）
        String normalizedType = normalizeExamType(type);

        // 4. 创建 Exam 对象
        Exam exam = Exam.builder()
            .name(name)
            .type(ExamStatus.valueOf(normalizedType))
            .classInfo(classInfo)
            .course(course)
            .examDate(examDate)
            .startTime(startTime)
            .endTime(endTime)
            .duration(duration)
            .fullScore(fullScore)
            .passScore(passScore)
            .location(location)
            .description(description)
            .status(ExamStatus.UPCOMING)
            .build();

        examRepository.save(exam);
        log.info("考试导入成功 - 名称：{}，课程：{}，班级：{}，日期：{}", 
            name, coursename, classname, examDate);
    }

    /**
     * 获取考试字段映射配置
     */
    public List<FieldMapping> getExamFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 考试名称
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("考试名称");
        name.setPossibleNames(Arrays.asList("考试名称", "名称", "考试名", "Exam Name", "Name", "标题"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);

        // 考试类型
        FieldMapping type = new FieldMapping();
        type.setTargetField("type");
        type.setFieldDescription("考试类型：模拟考(MOCK)/单元测试(UNIT)/月考(MONTHLY)/期中考试(MIDTERM)/期末考试(FINAL)");
        type.setPossibleNames(Arrays.asList("类型", "考试类型", "Type", "Exam Type", "类别"));
        type.setRequired(true);
        type.setDataType("string");
        type.setAllowedValues(Arrays.asList("MOCK", "UNIT", "MONTHLY", "MIDTERM", "FINAL", 
            "模拟考", "单元测试", "月考", "期中考试", "期末考试"));
        mappings.add(type);

        // 班级名称
        FieldMapping classname = new FieldMapping();
        classname.setTargetField("classname");
        classname.setFieldDescription("班级名称");
        classname.setPossibleNames(Arrays.asList("班级", "所属班级", "班别", "Class", "ClassName"));
        classname.setRequired(true);
        classname.setDataType("string");
        classname.setNeedExist(true);
        mappings.add(classname);

        // 课程名称
        FieldMapping coursename = new FieldMapping();
        coursename.setTargetField("coursename");
        coursename.setFieldDescription("课程名称");
        coursename.setPossibleNames(Arrays.asList("课程", "课程名称", "科目", "Course", "CourseName", "学科"));
        coursename.setRequired(true);
        coursename.setDataType("string");
        coursename.setNeedExist(true);
        mappings.add(coursename);

        // 考试日期
        FieldMapping examDate = new FieldMapping();
        examDate.setTargetField("examDate");
        examDate.setFieldDescription("考试日期，格式：yyyy-MM-dd");
        examDate.setPossibleNames(Arrays.asList("考试日期", "日期", "Exam Date", "Date", "考试时间"));
        examDate.setRequired(true);
        examDate.setDataType("string");
        mappings.add(examDate);

        // 开始时间
        FieldMapping startTime = new FieldMapping();
        startTime.setTargetField("startTime");
        startTime.setFieldDescription("开始时间，格式：HH:mm:ss");
        startTime.setPossibleNames(Arrays.asList("开始时间", "开始", "Start Time", "Start"));
        startTime.setRequired(false);
        startTime.setDataType("string");
        startTime.setRegex("^([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)$");
        mappings.add(startTime);

        // 结束时间
        FieldMapping endTime = new FieldMapping();
        endTime.setTargetField("endTime");
        endTime.setFieldDescription("结束时间，格式：HH:mm:ss");
        endTime.setPossibleNames(Arrays.asList("结束时间", "结束", "End Time", "End"));
        endTime.setRequired(false);
        endTime.setDataType("string");
        endTime.setRegex("^([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)$");
        mappings.add(endTime);

        // 时长
        FieldMapping duration = new FieldMapping();
        duration.setTargetField("duration");
        duration.setFieldDescription("考试时长（分钟）");
        duration.setPossibleNames(Arrays.asList("时长", "考试时长", "Duration", "分钟"));
        duration.setRequired(false);
        duration.setDataType("number");
        mappings.add(duration);

        // 总分
        FieldMapping fullScore = new FieldMapping();
        fullScore.setTargetField("fullScore");
        fullScore.setFieldDescription("总分");
        fullScore.setPossibleNames(Arrays.asList("总分", "满分", "Full Score", "Total Score"));
        fullScore.setRequired(false);
        fullScore.setDataType("number");
        mappings.add(fullScore);

        // 及格分
        FieldMapping passScore = new FieldMapping();
        passScore.setTargetField("passScore");
        passScore.setFieldDescription("及格分");
        passScore.setPossibleNames(Arrays.asList("及格分", "及格线", "Pass Score", "Passing Score"));
        passScore.setRequired(false);
        passScore.setDataType("number");
        mappings.add(passScore);

        // 考试地点
        FieldMapping location = new FieldMapping();
        location.setTargetField("location");
        location.setFieldDescription("考试地点");
        location.setPossibleNames(Arrays.asList("地点", "考场", "考试地点", "Location", "Room"));
        location.setRequired(false);
        location.setDataType("string");
        mappings.add(location);

        // 考试说明
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("考试说明");
        description.setPossibleNames(Arrays.asList("说明", "备注", "Description", "Remark"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        return mappings;
    }

    /**
     * 标准化考试类型
     */
    private String normalizeExamType(String type) {
        if (type == null) return "UNIT";
        
        switch (type) {
            case "模拟考":
            case "MOCK":
                return "MOCK";
            case "单元测试":
            case "UNIT":
                return "UNIT";
            case "月考":
            case "MONTHLY":
                return "MONTHLY";
            case "期中考试":
            case "MIDTERM":
                return "MIDTERM";
            case "期末考试":
            case "FINAL":
                return "FINAL";
            default:
                return type.toUpperCase();
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