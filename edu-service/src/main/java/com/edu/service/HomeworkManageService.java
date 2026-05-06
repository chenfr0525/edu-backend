package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkManageService {

    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final AiAnalysisReportRepository aiReportRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DeepSeekService deepSeekService;
    private final ActivitySyncService activitySyncService;
     private final UnifiedAiAnalysisService unifiedAiAnalysisService;

    private List<Long> getVisibleClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) return Arrays.asList(requestClassId);
            return classRepository.findAll().stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            List<ClassInfo> teacherClasses = classRepository.findByTeacher(teacher);
            if (requestClassId != null) {
                boolean hasAccess = teacherClasses.stream().anyMatch(c -> c.getId().equals(requestClassId));
                return hasAccess ? Arrays.asList(requestClassId) : new ArrayList<>();
            }
            return teacherClasses.stream().map(ClassInfo::getId).collect(Collectors.toList());
        }
    }

    private List<Long> getVisibleCourseIds(Long userId, String userRole, Long requestCourseId) {
        if ("ADMIN".equals(userRole)) {
            if (requestCourseId != null) return Arrays.asList(requestCourseId);
            return courseRepository.findAll().stream().map(Course::getId).collect(Collectors.toList());
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
            if (requestCourseId != null) {
                boolean hasAccess = teacherCourses.stream().anyMatch(c -> c.getId().equals(requestCourseId));
                return hasAccess ? Arrays.asList(requestCourseId) : new ArrayList<>();
            }
            return teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
        }
    }

    // ==================== 1. 作业列表 ====================

    @Transactional(readOnly = true)
    public Page<HomeworkListVO> getHomeworkList(HomeworkListRequest request, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, request.getCourseId());
        if (visibleCourseIds.isEmpty()) return Page.empty();
        
        // 如果指定了班级，需要进一步筛选
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, request.getClassId());
        
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        Page<Homework> homeworkPage = homeworkRepository.findWithFilters(
            visibleCourseIds,  request.getKeyword(), pageable);
        
        List<HomeworkListVO> voList = homeworkPage.getContent().stream()
            .map(hw -> convertToHomeworkListVO(hw, visibleClassIds))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return new PageImpl<>(voList, pageable, homeworkPage.getTotalElements());
    }

    private HomeworkListVO convertToHomeworkListVO(Homework homework, List<Long> visibleClassIds) {
        Course course = homework.getCourse();
        
        // 统计提交情况
        int totalStudents = (int) studentRepository.countByEnrollmentsCourse(course);
        int submittedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(homework.getId(),SubmissionStatus.PENDING );
        
        return HomeworkListVO.builder()
            .id(homework.getId())
            .name(homework.getName())
            .courseName(course.getName())
            .courseId(course.getId())
            .totalScore(homework.getTotalScore())
            .questionCount(homework.getQuestionCount())
            .status(homework.getStatus().toString())
            .statusText(getHomeworkStatusText(homework.getStatus()))
            .deadline(homework.getDeadline())
            .createdAt(homework.getCreatedAt())
            .avgScore(homework.getAvgScore())
            .passRate(homework.getPassRate())
            .description(homework.getDescription())
            .submittedCount(submittedCount)
            .totalStudents(totalStudents)
            .hasAiAnalysis(homework.getAiParsedData() != null)
            .build();
    }

    // ==================== 2. 统计卡片 ====================

    @Transactional(readOnly = true)
    public HomeworkStatisticsVO getStatistics(Long currentUserId, String userRole, Long classId, Long courseId) {
        List<Long> courseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (courseIds.isEmpty()) return buildEmptyStatistics();
        
        // 获取作业列表
        List<Homework> homeworks = homeworkRepository.findWithFilters(courseIds, null, Pageable.unpaged()).getContent();
        
        if (homeworks.isEmpty()) return buildEmptyStatistics();
        
        long totalHomework = homeworks.size();
        
        // 计算平均分
        double totalScore = 0;
        int scoreCount = 0;
        double totalPassRate = 0;
        int passRateCount = 0;
        double totalOnTimeRate = 0;
        
        for (Homework hw : homeworks) {
            if (hw.getAvgScore() != null) {
                totalScore += hw.getAvgScore().doubleValue();
                scoreCount++;
            }
            if (hw.getPassRate() != null) {
                totalPassRate += hw.getPassRate().doubleValue();
                passRateCount++;
            }
            
            // 按时提交率
            int totalStudents = (int) studentRepository.countByEnrollmentsCourse(hw.getCourse());
            if (totalStudents > 0) {
                int onTimeCount = (int) submissionRepository.countOnTimeByHomeworkId(hw.getId());
                totalOnTimeRate += onTimeCount * 100.0 / totalStudents;
            }
        }
        
        BigDecimal avgScore = scoreCount > 0 ? 
            BigDecimal.valueOf(totalScore / scoreCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgPassRate = passRateCount > 0 ? 
            BigDecimal.valueOf(totalPassRate / passRateCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal onTimeRate = homeworks.size() > 0 ? 
            BigDecimal.valueOf(totalOnTimeRate / homeworks.size()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        return HomeworkStatisticsVO.builder()
            .totalHomework(totalHomework)
            .avgScore(avgScore)
            .avgPassRate(avgPassRate)
            .onTimeRate(onTimeRate)
            .build();
    }

    private HomeworkStatisticsVO buildEmptyStatistics() {
        return HomeworkStatisticsVO.builder()
            .totalHomework(0L)
            .avgScore(BigDecimal.ZERO)
            .avgPassRate(BigDecimal.ZERO)
            .onTimeRate(BigDecimal.ZERO)
            .build();
    }

    // ==================== 3. 创建作业 ====================

    @Transactional
    public Homework createHomework(HomeworkCreateRequest request) {
         Course course = courseRepository.findById(request.getCourseId())
        .orElseThrow(() -> new RuntimeException("课程不存在"));
    
    // 确定作业状态
    HomeworkStatus status = HomeworkStatus.ONGOING;
    if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDateTime.now())) {
        status = HomeworkStatus.COMPLETED;
    }
        
      Homework homework = Homework.builder()
        .name(request.getName())
        .description(request.getDescription())
        .course(course)
        .questionCount(0)  // 默认0，不需要了
        .totalScore(request.getTotalScore() != null ? request.getTotalScore() : 100)
        .status(status)
        .deadline(request.getDeadline())
        .createdAt(LocalDateTime.now())
        .build();
    if (request.getKnowledgePointIds() != null && !request.getKnowledgePointIds().isEmpty()) {
        homework.setKnowledgePointIds(request.getKnowledgePointIds());
        log.info("创建作业时保存知识点ID列表: {}", request.getKnowledgePointIds());
    }
    
    return homeworkRepository.save(homework);
    }

    // ==================== 4. 作业详情 ====================

    @Transactional(readOnly = true)
    public HomeworkDetailVO getHomeworkDetail(Long homeworkId, Long currentUserId, String userRole) {
        Homework homework = homeworkRepository.findById(homeworkId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        Course course = homework.getCourse();
        
        // 权限检查
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, course.getId());
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该作业");
        }
        
        int totalStudents = (int) studentRepository.countByEnrollmentsCourse(course);
        int submittedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(homeworkId, SubmissionStatus.PENDING);
        int onTimeCount = (int) submissionRepository.countOnTimeByHomeworkId(homeworkId);
        
        BigDecimal submitRate = totalStudents > 0 ? 
            BigDecimal.valueOf(submittedCount * 100.0 / totalStudents).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        // 成绩分布
        ScoreDistributionDTO scoreDistribution = getScoreDistribution(homeworkId);
        
        // 学生成绩列表
        List<HomeworkStudentGradeVO> studentGrades = getStudentGrades(homeworkId);
        
        // 知识点错题分析
        List<KnowledgePointErrorVO> knowledgePointErrors = getKnowledgePointErrors(homeworkId, totalStudents);
        
        return HomeworkDetailVO.builder()
            .id(homework.getId())
            .name(homework.getName())
            .description(homework.getDescription())
            .courseName(course.getName())
            .courseId(course.getId())
            .totalScore(homework.getTotalScore())
            .questionCount(homework.getQuestionCount())
            .status(homework.getStatus().toString())
            .deadline(homework.getDeadline())
            .createdAt(homework.getCreatedAt())
            .avgScore(homework.getAvgScore())
            .passRate(homework.getPassRate())
            .totalStudents(totalStudents)
            .submittedCount(submittedCount)
            .submitRate(submitRate.toString())
            .onTimeCount(onTimeCount)
            .scoreDistribution(scoreDistribution)
            .studentGrades(studentGrades)
            .knowledgePointErrors(knowledgePointErrors)
            .build();
    }

    private ScoreDistributionDTO getScoreDistribution(Long homeworkId) {
        List<Integer> scores = submissionRepository.findScoresByHomeworkId(homeworkId);
        
        ScoreDistributionDTO distribution = new ScoreDistributionDTO();
        distribution.setExcellentCount(0);
        distribution.setGoodCount(0);
        distribution.setMediumCount(0);
        distribution.setPassCount(0);
        distribution.setFailCount(0);
        
        if (scores.isEmpty()) {
            distribution.setAverageScore(BigDecimal.ZERO);
            distribution.setHighestScore(BigDecimal.ZERO);
            distribution.setLowestScore(BigDecimal.ZERO);
            return distribution;
        }
        
        for (Integer score : scores) {
            if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
            else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
            else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
            else distribution.setFailCount(distribution.getFailCount() + 1);
        }
        
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        
        distribution.setAverageScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        distribution.setHighestScore(BigDecimal.valueOf(max));
        distribution.setLowestScore(BigDecimal.valueOf(min));
        
        return distribution;
    }

    private List<HomeworkStudentGradeVO> getStudentGrades(Long homeworkId) {
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homeworkId);
         if (submissions == null || submissions.isEmpty()) {
        return Collections.emptyList();
    }
    
        return submissions.stream()
            .<HomeworkStudentGradeVO>map(sub -> {
        Student student = sub.getStudent();
        User user = student.getUser();
        
        return HomeworkStudentGradeVO.builder()
            .studentId(student.getId())
            .studentNo(student.getStudentNo())
            .studentName(user != null ? user.getName() : "")
            .score(sub.getScore())
            .feedback(sub.getFeedback())
            .status(sub.getStatus().toString())
            // .isLate(sub.getSubmissionLateMinutes() > 0)
            .lateMinutes(sub.getSubmissionLateMinutes())
            .submittedAt(sub.getSubmittedAt())
            .build();
    })
            .collect(Collectors.toList());
    }

    private List<KnowledgePointErrorVO> getKnowledgePointErrors(Long homeworkId, int totalStudents) {
        List<KnowledgePointErrorVO> result = new ArrayList<>();
        
        // 从数据库查询知识点错误统计
        List<Object[]> rawErrors = submissionRepository.countKnowledgePointErrors(homeworkId);
        
        for (Object[] row : rawErrors) {
            Double kpScore = (Double) row[0];
            Long errorCount = (Long) row[1];
            
            // 这里需要获取知识点名称，简化处理
            result.add(KnowledgePointErrorVO.builder()
                .knowledgePointId(0L)
                .knowledgePointName("知识点")
                .errorCount(errorCount.intValue())
                .totalStudents(totalStudents)
                .errorRate(BigDecimal.valueOf(errorCount * 100.0 / totalStudents).setScale(2, RoundingMode.HALF_UP))
                .suggestion(generateKpSuggestion(errorCount, totalStudents))
                .build());
        }
        
        result.sort((a, b) -> b.getErrorRate().compareTo(a.getErrorRate()));
        return result;
    }

    private String generateKpSuggestion(long errorCount, int totalStudents) {
        double errorRate = errorCount * 100.0 / totalStudents;
        if (errorRate >= 70) return "🔴 严重薄弱点，建议安排专项复习";
        if (errorRate >= 50) return "🟡 薄弱点，需要加强练习";
        if (errorRate >= 30) return "🟢 基本掌握，可适当巩固";
        return "✅ 掌握良好，继续保持";
    }
/**
 * 编辑作业
 */
@Transactional
public Homework updateHomework(Long homeworkId, HomeworkUpdateRequest request, Long currentUserId, String userRole) {
    // 1. 查询作业是否存在
    Homework homework = homeworkRepository.findById(homeworkId)
        .orElseThrow(() -> new RuntimeException("作业不存在"));
    
    // 2. 权限检查
    List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, homework.getCourse().getId());
    if (visibleCourseIds.isEmpty()) {
        throw new RuntimeException("无权限编辑该作业");
    }
    
    // 3. 如果课程变更，检查新课程是否存在且有权访问
    if (request.getCourseId() != null && !request.getCourseId().equals(homework.getCourse().getId())) {
        Course newCourse = courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        // 检查新课程是否有权限
        List<Long> newCourseIds = getVisibleCourseIds(currentUserId, userRole, request.getCourseId());
        if (newCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        homework.setCourse(newCourse);
    }
    
    // 4. 如果知识点变更
    // if (request.getKnowledgePointId() != null && 
    //     (homework.getKnowledgePoint() == null || 
    //      !request.getKnowledgePointId().equals(homework.getKnowledgePoint().getId()))) {
    //     KnowledgePoint kp = knowledgePointRepository.findById(request.getKnowledgePointId())
    //         .orElse(null);
    //     homework.setKnowledgePoint(kp);
    // }
    
    // 5. 更新基本信息
    homework.setName(request.getName());
    homework.setDescription(request.getDescription());
    homework.setQuestionCount(request.getQuestionCount() != null ? request.getQuestionCount() : 0);
    homework.setTotalScore(request.getTotalScore() != null ? request.getTotalScore() : 100);
    homework.setDeadline(request.getDeadline());
    
    // 6. 更新状态
    if (request.getStatus() != null) {
        homework.setStatus(request.getStatus());
    }
    
    // 7. 保存更新
    return homeworkRepository.save(homework);
}

    // ==================== 5. AI解析作业文件 ====================
    /**
     * 作业解析阶段字段映射 - 用于AI解析Excel文件
     * 字段名：用户友好的名称（课程名称、班级名称、知识点名称）
     */
    public List<FieldMapping> getHomeworkParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        // 作业名称（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("作业名称");
        name.setPossibleNames(Arrays.asList("作业名称", "名称", "作业名", "标题", "Homework Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);
        
         // 课程名称（必填，需存在）
        FieldMapping courseName = new FieldMapping();
        courseName.setTargetField("courseName");
        courseName.setFieldDescription("课程名称");
        courseName.setPossibleNames(Arrays.asList("课程", "课程名称", "科目", "Course"));
        courseName.setRequired(true);
        courseName.setDataType("string");
        courseName.setNeedExist(true);
        mappings.add(courseName);
        
        // 截止日期（必填）
        FieldMapping deadline = new FieldMapping();
        deadline.setTargetField("deadline");
        deadline.setFieldDescription("截止时间，格式：yyyy-MM-dd HH:mm:ss");
        deadline.setPossibleNames(Arrays.asList("截止时间", "截止日期", "Deadline", "提交截止"));
        deadline.setRequired(true);
        deadline.setDataType("string");
        mappings.add(deadline);

        // 班级名称（可选）
        FieldMapping className = new FieldMapping();
        className.setTargetField("className");
        className.setFieldDescription("班级名称");
        className.setPossibleNames(Arrays.asList("班级", "所属班级", "班别", "Class", "ClassName"));
        className.setRequired(false);
        className.setDataType("string");
        mappings.add(className);
        
         // 总分（可选，默认100）
        FieldMapping totalScore = new FieldMapping();
        totalScore.setTargetField("totalScore");
        totalScore.setFieldDescription("总分");
        totalScore.setPossibleNames(Arrays.asList("总分", "满分", "Total Score"));
        totalScore.setRequired(false);
        totalScore.setDataType("number");
        mappings.add(totalScore);
        
       // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("作业描述");
        description.setPossibleNames(Arrays.asList("描述", "说明", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        // 知识点名称（可选）- 解析时用名称，导入时转为ID列表
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
 * 作业导入阶段字段映射 - 用于验证前端传来的数据
 * 字段名：代码中使用的字段名（courseId, classId, knowledgePointIds）
 */
public List<FieldMapping> getHomeworkImportFieldMappings() {
    List<FieldMapping> mappings = new ArrayList<>();

    // 作业名称（必填）
    FieldMapping name = new FieldMapping();
    name.setTargetField("name");
    name.setFieldDescription("作业名称");
    name.setPossibleNames(Arrays.asList("name", "作业名称"));
    name.setRequired(true);
    name.setDataType("string");
    mappings.add(name);

    // 课程ID（必填）
    FieldMapping courseId = new FieldMapping();
    courseId.setTargetField("courseId");
    courseId.setFieldDescription("课程ID");
    courseId.setPossibleNames(Arrays.asList("courseId", "课程ID"));
    courseId.setRequired(true);
    courseId.setDataType("number");
    courseId.setNeedExist(true);
    mappings.add(courseId);

    // 截止时间（必填）
    FieldMapping deadline = new FieldMapping();
    deadline.setTargetField("deadline");
    deadline.setFieldDescription("截止时间");
    deadline.setPossibleNames(Arrays.asList("deadline", "截止时间"));
    deadline.setRequired(true);
    deadline.setDataType("string");
    mappings.add(deadline);

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
    FieldMapping totalScore = new FieldMapping();
    totalScore.setTargetField("totalScore");
    totalScore.setFieldDescription("总分");
    totalScore.setPossibleNames(Arrays.asList("totalScore", "总分"));
    totalScore.setRequired(false);
    totalScore.setDataType("number");
    mappings.add(totalScore);

    // 描述（可选）
    FieldMapping description = new FieldMapping();
    description.setTargetField("description");
    description.setFieldDescription("作业描述");
    description.setPossibleNames(Arrays.asList("description", "描述"));
    description.setRequired(false);
    description.setDataType("string");
    mappings.add(description);

    return mappings;
}

// HomeworkManageService.java - 修改 parseFile 相关方法

/**
 * AI解析作业文件后，自动将名称转换为ID
 */
public ParseResult parseAndConvertHomeworkFile(String fileContent, String fileName, 
                                                Long currentUserId, String userRole) {
    // 1. 获取解析阶段的字段映射
    List<FieldMapping> mappings = getHomeworkParseFieldMappings();
    
    // 2. AI解析文件
    ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "homework", mappings);
    
    // 3. 解析成功后，自动转换名称到ID
    if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
        convertHomeworkParseResultToIds(result, currentUserId, userRole);
    }
    
    return result;
}

/**
 * 将解析结果中的名称转换为ID
 */
private void convertHomeworkParseResultToIds(ParseResult result, Long currentUserId, String userRole) {
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
        String courseName = (String) row.get("courseName");
        if (courseName != null && !courseName.isEmpty()) {
            Long courseId = courseNameToIdMap.get(courseName);
            if (courseId != null) {
                row.put("courseId", courseId);
                // 保留原始名称供前端显示
                // row.put("courseName", courseName); // 保留不变
            } else {
                row.put("courseId", null);
                row.put("_error_courseName", "课程不存在: " + courseName);
            }
        }
        
        // 2. 班级名称 -> classId
        String className = (String) row.get("className");
        if (className != null && !className.isEmpty()) {
            Long classId = classNameToIdMap.get(className);
            if (classId != null) {
                row.put("classId", classId);
            } else {
                row.put("classId", null);
                row.put("_error_className", "班级不存在: " + className);
            }
        }
        
        // 3. 知识点名称 -> knowledgePointIds
        Long courseId = (Long) row.get("courseId");
        String knowledgePointNames = (String) row.get("knowledgePointNames");
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
        
        // 4. 转换截止时间格式（AI返回的是 "2026-03-26T00:00:00"）
        String deadline = (String) row.get("deadline");
        if (deadline != null) {
            try {
                // 处理 Excel 数字日期格式
                if (deadline.matches("\\d+")) {
                    long excelDateNum = Long.parseLong(deadline);
                    LocalDate excelDate = LocalDate.of(1900, 1, 1).plusDays(excelDateNum - 2);
                    row.put("deadline", excelDate.atStartOfDay().toString());
                }
            } catch (Exception e) {
                log.warn("日期格式转换失败: {}", deadline);
            }
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


    @Transactional
    public ImportResult confirmHomeworkImport(List<Map<String, Object>> data, User user) {
        List<FieldMapping> mappings = getHomeworkImportFieldMappings();
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
                insertSingleHomework(row, teacherId);
                successCount++;
                log.info("成功导入作业：{}", row.get("name"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("第%d行 - 作业名称：%s，原因：%s",
                    i + 1, row.get("name"), e.getMessage());
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
       return ImportResult.builder()
        .success(allSuccess)
        .successCount(successCount)
        .failCount(failCount)
        .errors(errorDetails)
        .message(message)
        .build();
    }



    private void insertSingleHomework(Map<String, Object> row, Long teacherId) {
        String name = (String) row.get("name");
        Long courseId = null;
        Object courseIdObj = row.get("courseId");
            if (courseIdObj != null) {
                if (courseIdObj instanceof Number) {
                    courseId = ((Number) courseIdObj).longValue();
                } else if (courseIdObj instanceof String) {
                    try {
                        courseId = Long.parseLong((String) courseIdObj);
                    } catch (NumberFormatException e) {
                        // 不是数字，忽略
                    }
                }
            }
        String deadlineStr = (String) row.get("deadline");
        // 可选字段
        Integer totalScore = row.get("totalScore") != null ? ((Number) row.get("totalScore")).intValue() : 100;
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
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (!courseOptional.isPresent()) {
            throw new RuntimeException("课程 " + courseId + " 不存在");
        }
        Course course = courseOptional.get();

        // 2. 解析截止时间
        LocalDateTime deadline = parseDateTime(deadlineStr);
        
        // 3. 确定作业状态（根据截止时间自动判断）
        HomeworkStatus status = HomeworkStatus.ONGOING;
        if (deadline.isBefore(LocalDateTime.now())) {
            status = HomeworkStatus.COMPLETED;  // 已过期的作业状态为已完成
        }
        
        // 4. 创建作业
        Homework homework = Homework.builder()
            .name(name)
            .description(description)
            .course(course)
            .questionCount(0)
            .totalScore(totalScore)
            .status(status)
            .deadline(deadline)
            .knowledgePointIds(knowledgePointIds)  // 设置知识点ID列表
            .createdAt(LocalDateTime.now())
            .build();
        
        homeworkRepository.save(homework);
        log.info("作业导入成功 - 名称：{}，课程：{}，截止时间：{}", name, courseId, deadline);
    }

    // ==================== 6. AI解析作业成绩文件 ====================
    public List<FieldMapping> getHomeworkGradeParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
         // 学生（必填，可用学号或姓名）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);
        
       // 成绩（必填）
        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("作业得分");
        score.setPossibleNames(Arrays.asList("成绩", "分数", "得分", "Score"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);
        
        // 批注（可选）
        FieldMapping feedback = new FieldMapping();
        feedback.setTargetField("feedback");
        feedback.setFieldDescription("评语");
        feedback.setPossibleNames(Arrays.asList("评语", "反馈", "Feedback", "Comment"));
        feedback.setRequired(false);
        feedback.setDataType("string");
        mappings.add(feedback);
        
        return mappings;
    }

    /**
 * 作业成绩导入阶段字段映射 - 用于验证前端传来的数据
 * （与解析阶段基本相同，因为字段名一致）
 */
public List<FieldMapping> getHomeworkGradeImportFieldMappings() {
    List<FieldMapping> mappings = new ArrayList<>();

    // 学生（必填）
    FieldMapping studentName = new FieldMapping();
    studentName.setTargetField("studentName");
    studentName.setFieldDescription("学生姓名或学号");
    studentName.setPossibleNames(Arrays.asList("studentName", "学生"));
    studentName.setRequired(true);
    studentName.setDataType("string");
    studentName.setNeedExist(true);
    mappings.add(studentName);

    // 成绩（必填）
    FieldMapping score = new FieldMapping();
    score.setTargetField("score");
    score.setFieldDescription("作业得分");
    score.setPossibleNames(Arrays.asList("score", "成绩"));
    score.setRequired(true);
    score.setDataType("number");
    mappings.add(score);

    // 批注（可选）
    FieldMapping feedback = new FieldMapping();
    feedback.setTargetField("feedback");
    feedback.setFieldDescription("评语");
    feedback.setPossibleNames(Arrays.asList("feedback", "批注"));
    feedback.setRequired(false);
    feedback.setDataType("string");
    mappings.add(feedback);

    return mappings;
}

     /**
     * 构建错误消息
     */
    private String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 确认导入作业成绩（修改）
     */
    @Transactional
    public HomeworkGradeImportResultVO confirmHomeworkGradeImport(Long homeworkId, List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getHomeworkGradeImportFieldMappings();
    
        // 1. 验证数据
        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return HomeworkGradeImportResultVO.builder()
                .success(false)
                .message(buildErrorMessage(errors))
                .build();
        }

        // 2. 查询作业
        Homework homework = homeworkRepository.findById(homeworkId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        // 3. 批量导入成绩
        int successCount = 0;
        int failCount = 0;
        int updateCount = 0;
        List<Submission> savedSubmissions = new ArrayList<>();
        StringBuilder resultMsg = new StringBuilder();
    
        for (int i = 0; i < data.size(); i++) {
        Map<String, Object> row = data.get(i);
        try {
            // 查找学生
            String studentIdentifier = (String) row.get("studentName");
            Student student = findStudentByIdentifier(studentIdentifier);
            if (student == null) {
                throw new RuntimeException("学生不存在: " + studentIdentifier);
            }
            
            // 判断是新增还是更新
            Submission existing = submissionRepository.findByHomeworkAndStudent(homework, student);
            if (existing != null) {
                updateCount++;
            }
            
            Submission submission = insertOrUpdateSubmission(homework, row, student);
            savedSubmissions.add(submission);
            successCount++;
            log.info("成功导入作业成绩 - 第{}行，学生：{}，成绩：{}", 
                i + 1, row.get("studentName"), row.get("score"));
                
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("第%d行 - 学生：%s，原因：%s",
                    i + 1, row.get("studentName"), e.getMessage());
                log.error(errorMsg);
                resultMsg.append(errorMsg).append("\n");
            }
        }

        // 4. 更新作业统计（平均分、及格率）
        updateHomeworkStatistics(homework);
        
        // 5. 检查是否需要更新作业状态（全部学生都已批改）
        updateHomeworkStatusIfComplete(homework);
        
        // 6. 处理知识点掌握度并触发AI分析
        boolean aiCompleted = processHomeworkGradesAndUpdateMastery(homework, savedSubmissions);
        
        String summary = String.format("导入完成！成功：%d条（新增%d条，更新%d条），失败：%d条", 
            successCount, successCount - updateCount, updateCount, failCount);
        if (failCount > 0) {
            summary = summary + "\n" + resultMsg.toString();
        }
        log.info(summary);

        return HomeworkGradeImportResultVO.builder()
            .homeworkId(homeworkId)
            .totalImported(data.size())
            .successCount(successCount)
            .updateCount(updateCount)
            .failCount(failCount)
            .success(failCount == 0)
            .message(summary)
            .aiAnalysisCompleted(aiCompleted)
            .build();
    }

    /**
     * 更新作业统计（平均分、及格率）
     */
    private void updateHomeworkStatistics(Homework homework) {
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        if (submissions.isEmpty()) return;
        
        double avg = submissions.stream()
            .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0)
            .average()
            .orElse(0);
        
        long passCount = submissions.stream()
            .filter(s -> s.getScore() != null && s.getScore() >= 60)
            .count();
        
        homework.setAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        homework.setPassRate(BigDecimal.valueOf(passCount * 100.0 / submissions.size()).setScale(2, RoundingMode.HALF_UP));
        homeworkRepository.save(homework);
    }

    /**
     * 检查并更新作业状态（全部学生都已批改时，状态变为COMPLETED）
     */
    private void updateHomeworkStatusIfComplete(Homework homework) {
        // 获取该课程所有选课学生数
        int totalStudents = (int) studentRepository.countByEnrollmentsCourse(homework.getCourse());
        // 获取已批改的学生数
        int gradedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(
            homework.getId(), SubmissionStatus.PENDING);
        
        // 如果全部学生都已批改，状态变为COMPLETED
        if (gradedCount >= totalStudents && totalStudents > 0) {
            homework.setStatus(HomeworkStatus.COMPLETED);
            homeworkRepository.save(homework);
            log.info("作业 {} 所有学生已批改，状态更新为COMPLETED", homework.getName());
        }
    }


   private Submission insertOrUpdateSubmission(Homework homework, Map<String, Object> row, Student student) {
        Double score = ((Number) row.get("score")).doubleValue();
        String feedback = (String) row.get("feedback");
    
        // 1. 自动创建选课记录
        ensureEnrollment(student, homework.getCourse());
        
        // 2. 查找已有提交记录
        Submission submission = submissionRepository.findByHomeworkAndStudent(homework, student);
        boolean isNew = (submission == null);
    
        if (isNew) {
            submission = new Submission();
            submission.setHomework(homework);
            submission.setStudent(student);
            submission.setStatus(SubmissionStatus.GRADED);
            submission.setSubmittedAt(LocalDateTime.now());
        }
    
        // 3. 设置批改信息
        submission.setScore(score);
        submission.setFeedback(feedback);
        submission.setGradedAt(LocalDateTime.now());

        // 4. 保存提交记录
        Submission saved = submissionRepository.save(submission);
        
        // 5. 同步活动记录
        try {
            activitySyncService.syncHomeworkActivity(student, homework, score);
        } catch (Exception e) {
            log.error("同步作业活动记录失败", e);
        }
        
        return saved;
}
 /**
     * 处理作业成绩并更新知识点掌握度
     */
    private boolean processHomeworkGradesAndUpdateMastery(Homework homework, List<Submission> submissions) {
        try {
            // 获取作业关联的知识点ID列表
            List<Long> knowledgePointIds = homework.getKnowledgePointIds();
            
            if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
                log.warn("作业 {} 未关联知识点，跳过掌握度计算", homework.getId());
                return false;
            }

            List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
            if (kps.isEmpty()) {
                log.warn("未找到知识点信息，跳过掌握度计算");
                return false;
            }

             // 1. 更新学生知识点掌握度
            for (Submission submission : submissions) {
                Student student = submission.getStudent();
                Double submissionScore = submission.getScore();
                
                double scoreRate = (submissionScore != null && homework.getTotalScore() != null && homework.getTotalScore() > 0)
                    ? submissionScore / homework.getTotalScore() * 100
                    : (submissionScore != null ? submissionScore : 0);
                
                for (KnowledgePoint kp : kps) {
                    saveKnowledgePointScoreDetail(student, kp, "HOMEWORK", homework.getId(), scoreRate);
                    updateStudentMastery(student, kp, scoreRate);
                }
            }
            // 2. 使用统一服务生成AI分析报告（自动保存）
            AiSuggestionDTO aiResult = unifiedAiAnalysisService.getOrCreateAnalysis(
                "HOMEWORK",                    // targetType: 作业类型
                homework.getId(),              // targetId: 作业ID
                "HOMEWORK_ANALYSIS",           // reportType: 作业分析
                true                           // 强制刷新
            );
            
            log.info("作业成绩AI分析完成，作业ID: {}, 摘要: {}", 
                homework.getId(), aiResult != null ? aiResult.getSummary() : "无");
            
            return true;
            
        } catch (Exception e) {
            log.error("处理作业成绩失败", e);
            return false;
        }
    }
private void updateStudentMastery(Student student, KnowledgePoint kp, double newScoreRate) {
    Optional<StudentKnowledgeMastery> existing = masteryRepository
        .findByStudentAndKnowledgePoint(student, kp);
    
    StudentKnowledgeMastery mastery;
    if (existing.isPresent()) {
        mastery = existing.get();
    } else {
        mastery = new StudentKnowledgeMastery();
        mastery.setStudent(student);
        mastery.setKnowledgePoint(kp);
    }

    // 按 student + knowledgePoint 的 actual_score 均值重算掌握度，保持兼容字段 score/masteryLevel 一致
    List<KnowledgePointScoreDetail> allDetails = kpScoreDetailRepository.findByStudentAndKnowledgePoint(student, kp);
    double avgActualScore = allDetails.stream()
        .filter(Objects::nonNull)
        .map(KnowledgePointScoreDetail::getActualScore)
        .filter(Objects::nonNull)
        .mapToDouble(BigDecimal::doubleValue)
        .average()
        .orElse(newScoreRate);

    double normalized = Math.min(Math.max(avgActualScore, 0D), 100D);
    mastery.setMasteryLevel(normalized);
    mastery.setScore(normalized);
    mastery.setUpdatedAt(LocalDateTime.now());
    
    // 设置薄弱程度
    double level = mastery.getMasteryLevel();
    if (level < 50) mastery.setWeaknessLevel("SEVERE");
     else if (level < 60) mastery.setWeaknessLevel("MODERATE");
    else if (level < 70) mastery.setWeaknessLevel("MILD");
    else mastery.setWeaknessLevel("GOOD");
    
    masteryRepository.save(mastery);
}

private void saveKnowledgePointScoreDetail(Student student, KnowledgePoint kp, 
        String sourceType, Long sourceId, double scoreRate) {
    KnowledgePointScoreDetail detail = kpScoreDetailRepository
        .findFirstByStudentAndKnowledgePointAndSourceTypeAndSourceIdOrderByCreatedAtDesc(
            student, kp, sourceType, sourceId)
        .orElseGet(() -> {
            KnowledgePointScoreDetail created = new KnowledgePointScoreDetail();
            created.setStudent(student);
            created.setKnowledgePoint(kp);
            created.setSourceType(sourceType);
            created.setSourceId(sourceId);
            created.setCreatedAt(LocalDateTime.now());
            return created;
        });

    BigDecimal normalized = BigDecimal.valueOf(scoreRate).setScale(2, RoundingMode.HALF_UP);
    detail.setScoreRate(normalized);
    // 兼容既有展示：使用百分制保存
    detail.setMaxScore(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
    detail.setActualScore(normalized);

    kpScoreDetailRepository.save(detail);
}
    private void ensureEnrollment(Student student, Course course) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentAndCourse(student, course);
        if (enrollments.isEmpty()) {
            Enrollment newEnrollment = new Enrollment();
            newEnrollment.setStudent(student);
            newEnrollment.setCourse(course);
            newEnrollment.setStatus(CourseStatus.ONGOING);
            newEnrollment.setProgress(0);
            newEnrollment.setEnrolledAt(LocalDateTime.now());
            enrollmentRepository.save(newEnrollment);
            log.info("自动创建选课记录 - 学生：{}，课程：{}", 
                student.getUser().getName(), course.getName());
        }
    }
    @Transactional
    public void deleteHomework(Long homeworkId, Long currentUserId, String userRole) {
        Homework homework = homeworkRepository.findById(homeworkId)
            .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        // 权限检查
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, homework.getCourse().getId());
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限删除该作业");
        }
        
        // 先删除关联的提交记录
        List<Submission> submissions = submissionRepository.findByHomework(homework);
        submissionRepository.deleteAll(submissions);
        
        homeworkRepository.delete(homework);
    }

    // ==================== 辅助方法 ====================

    private Student findStudentByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        
        String trimmedIdentifier = identifier.trim();
        
        Optional<Student> studentByNo = studentRepository.findByStudentNo(trimmedIdentifier);
        if (studentByNo.isPresent()) return studentByNo.get();
        
        Optional<User> userByUsername = userRepository.findByUsername(trimmedIdentifier);
        if (userByUsername.isPresent()) {
            User user = userByUsername.get();
            if (user.getRole() == Role.STUDENT) {
                return studentRepository.findByUser(user).orElse(null);
            }
        }
        
        List<User> usersByName = userRepository.findByName(trimmedIdentifier);
        for (User user : usersByName) {
            if (user.getRole() == Role.STUDENT) {
                Optional<Student> student = studentRepository.findByUser(user);
                if (student.isPresent()) return student.get();
            }
        }
        
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) throw new RuntimeException("日期时间不能为空");
        
        try {
            return LocalDateTime.parse(dateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr + "T00:00:00", java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                throw new RuntimeException("日期时间格式无效: " + dateStr);
            }
        }
    }
    private String getHomeworkStatusText(HomeworkStatus status) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("ONGOING", "进行中");
        statusMap.put("PENDING", "待发布");
        statusMap.put("COMPLETED", "已完成");
        statusMap.put("EXPIRED", "已过期");
        return statusMap.getOrDefault(status.toString(), status.toString());
    }
}
