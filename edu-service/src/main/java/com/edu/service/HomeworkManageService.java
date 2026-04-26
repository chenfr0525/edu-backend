// HomeworkManageService.java
package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final SemesterRepository semesterRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;
    private final ActivitySyncService activitySyncService;

    // ==================== 权限辅助方法 ====================

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

            KnowledgePoint knowledgePoint=null;
        
             if (request.getKnowledgePointId() != null) {
         knowledgePoint = knowledgePointRepository.findById(request.getKnowledgePointId())
            .orElse(null);
             }
        
        Homework homework = Homework.builder()
            .name(request.getName())
            .description(request.getDescription())
            .course(course)
            .questionCount(request.getQuestionCount() != null ? request.getQuestionCount() : 0)
            .totalScore(request.getTotalScore() != null ? request.getTotalScore() : 100)
            .status(request.getStatus())
            .deadline(request.getDeadline())
            .createdAt(LocalDateTime.now())
            .build();
        
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
    public List<FieldMapping> getHomeworkFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("作业名称");
        name.setPossibleNames(Arrays.asList("作业名称", "名称", "作业名", "标题", "Homework Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);
        
        FieldMapping courseName = new FieldMapping();
        courseName.setTargetField("courseName");
        courseName.setFieldDescription("课程名称");
        courseName.setPossibleNames(Arrays.asList("课程", "课程名称", "科目", "Course"));
        courseName.setRequired(true);
        courseName.setDataType("string");
        courseName.setNeedExist(true);
        mappings.add(courseName);
        
        FieldMapping deadline = new FieldMapping();
        deadline.setTargetField("deadline");
        deadline.setFieldDescription("截止时间");
        deadline.setPossibleNames(Arrays.asList("截止时间", "截止日期", "Deadline", "提交截止"));
        deadline.setRequired(true);
        deadline.setDataType("string");
        mappings.add(deadline);
        
        FieldMapping totalScore = new FieldMapping();
        totalScore.setTargetField("totalScore");
        totalScore.setFieldDescription("总分");
        totalScore.setPossibleNames(Arrays.asList("总分", "满分", "Total Score"));
        totalScore.setRequired(false);
        totalScore.setDataType("number");
        mappings.add(totalScore);
        
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("作业描述");
        description.setPossibleNames(Arrays.asList("描述", "说明", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);
        
        return mappings;
    }

    @Transactional
    public String confirmHomeworkImport(List<Map<String, Object>> data,User user) {
        List<FieldMapping> mappings= getHomeworkFieldMappings();
         List<ValidationError> errors = deepSeekService.validateData(data,mappings);
         Long teacherId=teacherRepository.findByUser(user).get().getId();
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
        Homework savedHomework = null;
        
        for (Map<String, Object> row : data) {
            try {
                insertSingleHomework(row,teacherId);
                successCount++;
                log.info("成功导入作业：{}", row.get("name"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("导入失败 - 作业名称：%s，原因：%s",
                    row.get("name"), e.getMessage());
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

    private void insertSingleHomework(Map<String, Object> row,Long teacherId) {
        String name = (String) row.get("name");
        String courseName = (String) row.get("courseName");
        String deadlineStr = (String) row.get("deadline");
        Integer totalScore = row.get("totalScore") != null ? ((Number) row.get("totalScore")).intValue() : 100;
        String description = (String) row.get("description");
        
        List<Course> courses = courseRepository.findByNameAndTeacherId( teacherId,courseName);
        if (courses == null || courses.isEmpty()) {
            log.warn("未找到课程: {}", courseName);
            throw new RuntimeException("课程 " + courseName + " 不存在");
        }
        Course course = courses.get(0);
        LocalDateTime deadline = parseDateTime(deadlineStr);
        
        Homework homework = Homework.builder()
            .name(name)
            .description(description)
            .course(course)
            .questionCount(0)
            .totalScore(totalScore)
            .status(HomeworkStatus.ONGOING)
            .deadline(deadline)
            .createdAt(LocalDateTime.now())
            .build();
        
        homeworkRepository.save(homework);
        log.info("作业插入成功");
    }

    // ==================== 6. AI解析作业成绩文件 ====================
    public List<FieldMapping> getHomeworkGradeFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);
        
        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("作业得分");
        score.setPossibleNames(Arrays.asList("成绩", "分数", "得分", "Score"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);
        
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
     * 构建错误消息
     */
    private String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public HomeworkGradeImportResultVO confirmHomeworkGradeImport(Long homeworkId, List<Map<String, Object>> data) {
         List<FieldMapping> mappings= getHomeworkGradeFieldMappings();
      
             List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return HomeworkGradeImportResultVO.builder()
                .success(false)
                .message(buildErrorMessage(errors))
                .build();
        }

           Homework homework = homeworkRepository.findById(homeworkId)
        .orElseThrow(() -> new RuntimeException("作业不存在"));
    
    int successCount = 0;
    int failCount = 0;
    int updateCount = 0;
    List<Submission> savedSubmissions = new ArrayList<>();
    StringBuilder resultMsg = new StringBuilder();
    
    for (Map<String, Object> row : data) {
        try {
            String studentIdentifier = (String) row.get("studentName");
            Student student = findStudentByIdentifier(studentIdentifier);
            if (student == null) {
                throw new RuntimeException("学生不存在: " + studentIdentifier);
            }
            Submission submission = insertOrUpdateSubmission(homework, row, student);
            savedSubmissions.add(submission);
            successCount++;
            if (submission.getId() != null && submissionRepository.findById(submission.getId()).isPresent()) {
                updateCount++;
            }
            log.info("成功导入成绩 - 学生：{}，成绩：{}", row.get("studentName"), row.get("score"));
        } catch (Exception e) {
            failCount++;
            String errorMsg = String.format("导入失败 - 学生：%s，原因：%s",
                row.get("studentName"), e.getMessage());
            log.error(errorMsg);
            resultMsg.append(errorMsg).append("\n");
        }
    }
    
    // 更新作业统计
    updateHomeworkStatistics(homework);

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

   private Submission insertOrUpdateSubmission(Homework homework, Map<String, Object> row, Student student) {
    Double score = ((Number) row.get("score")).doubleValue();
    String feedback = (String) row.get("feedback");
    
    // 自动创建选课记录
    ensureEnrollment(student, homework.getCourse());
    
    Submission submission = submissionRepository.findByHomeworkAndStudent(homework, student);
    boolean isNew = (submission == null);
    
    if (isNew) {
        submission = new Submission();
        submission.setHomework(homework);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setSubmittedAt(LocalDateTime.now());
    }
    
    submission.setScore(score);
    submission.setFeedback(feedback);

    Submission saved = submissionRepository.save(submission);
    
    // ========== 新增：同步作业提交活动记录 ==========
    try {
        activitySyncService.syncHomeworkActivity(student, homework, score);
    } catch (Exception e) {
        log.error("同步作业活动记录失败", e);
    }
    
    return saved;
}

private boolean processHomeworkGradesAndUpdateMastery(Homework homework, List<Submission> submissions) {
    try {
        // 获取作业关联的知识点ID列表
        List<Long> knowledgePointIds = homework.getKnowledgePointIds();
        
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            log.warn("作业 {} 未关联知识点，跳过掌握度计算", homework.getId());
            return false;
        }
        
        // 获取知识点信息
        List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
        
        if (kps.isEmpty()) {
            log.warn("未找到知识点信息，跳过掌握度计算");
            return false;
        }

        // 统计信息用于AI分析
        List<Double> scores = submissions.stream()
            .map(Submission::getScore)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        long passCount = scores.stream().filter(s -> s >= 60).count();
        long excellentCount = scores.stream().filter(s -> s >= 80).count();
        
        // 构建AI分析数据
        Map<String, Object> aiAnalysisData = new HashMap<>();
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

         // ========== 核心：更新学生知识点掌握度 ==========
        for (Submission submission : submissions) {
            Student student = submission.getStudent();
            Double submissionScore = submission.getScore();
            
            // 计算得分率（满分100分制）
            double scoreRate = (submissionScore != null && homework.getTotalScore() != null && homework.getTotalScore() > 0)
                ? submissionScore / homework.getTotalScore() * 100
                : (submissionScore != null ? submissionScore : 0);
            
            for (KnowledgePoint kp : kps) {
                // 更新 student_knowledge_mastery 表
                updateStudentMastery(student, kp, scoreRate);
                
                // 更新 knowledge_point_score_detail 表
                saveKnowledgePointScoreDetail(student, kp, "HOMEWORK", homework.getId(), scoreRate);
            }
        }

          // 分析知识点掌握情况（用于AI报告）
        List<Map<String, Object>> kpAnalysis = new ArrayList<>();
        for (KnowledgePoint kp : kps) {
            Map<String, Object> kpResult = new HashMap<>();
            kpResult.put("knowledgePointId", kp.getId());
            kpResult.put("knowledgePointName", kp.getName());
            
            // 计算该知识点在本次作业中的平均掌握度
            double avgRate = calculateAvgMasteryForKnowledgePoint(homework, kp, submissions);
            kpResult.put("avgScoreRate", Math.round(avgRate * 100) / 100.0);
            
            String level = avgRate >= 70 ? "GOOD" : (avgRate >= 50 ? "MODERATE" : "WEAK");
            kpResult.put("level", level);
            kpAnalysis.add(kpResult);

                if (avgRate >= 70) strengths.add(kp.getName());
            else if (avgRate < 50) weaknesses.add(kp.getName());
        }
        
        // 生成AI分析摘要和建议
        String summary = generateHomeworkSummary(homework, avg, passCount, scores.size());
        String suggestions = generateHomeworkSuggestions(kpAnalysis, avg);
        
        Map<String, Object> scoreDistribution = new HashMap<>();
        scoreDistribution.put("avgScore", avg);
        scoreDistribution.put("passRate", scores.size() > 0 ? passCount * 100.0 / scores.size() : 0);

        scoreDistribution.put("excellentRate", scores.size() > 0 ? excellentCount * 100.0 / scores.size() : 0);
        
        aiAnalysisData.put("summary", summary);
        aiAnalysisData.put("strengths", strengths);
        aiAnalysisData.put("weaknesses", weaknesses);
        aiAnalysisData.put("suggestions", suggestions);
        aiAnalysisData.put("knowledgePointAnalysis", kpAnalysis);
        aiAnalysisData.put("scoreDistribution", scoreDistribution);
        
        // 保存到 ai_analysis_report 表（不再存到 homework.aiParsedData）
        saveAiAnalysisReport(homework, "HOMEWORK", summary, suggestions, aiAnalysisData);
        
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
        // 加权平均：旧值70% + 新值30%
        double newLevel = mastery.getMasteryLevel() * 0.7 + newScoreRate * 0.3;
        mastery.setMasteryLevel(Math.min(newLevel, 100));
    } else {
        mastery = new StudentKnowledgeMastery();
        mastery.setStudent(student);
        mastery.setKnowledgePoint(kp);
        mastery.setMasteryLevel(newScoreRate);
    }
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
    KnowledgePointScoreDetail detail = new KnowledgePointScoreDetail();
    detail.setStudent(student);
    detail.setKnowledgePoint(kp);
    detail.setSourceType(sourceType);
    detail.setSourceId(sourceId);
    detail.setScoreRate(BigDecimal.valueOf(scoreRate));
    // 满分100分，实际得分 = 得分率
    detail.setMaxScore(BigDecimal.valueOf(100));
    detail.setActualScore(BigDecimal.valueOf(scoreRate));
    detail.setCreatedAt(LocalDateTime.now());
    
    kpScoreDetailRepository.save(detail);
}

private double calculateAvgMasteryForKnowledgePoint(Homework homework, KnowledgePoint kp, List<Submission> submissions) {
    if (submissions.isEmpty()) return 0;
    
    // 简化逻辑：如果作业关联了该知识点，则使用学生的作业得分率
    // 实际可以根据题目-知识点映射更精确计算
    double total = 0;
    int count = 0;
    for (Submission sub : submissions) {
        if (sub.getScore() != null) {
            double scoreRate = (homework.getTotalScore() != null && homework.getTotalScore() > 0)
                ? sub.getScore() / homework.getTotalScore() * 100
                : sub.getScore();
            total += scoreRate;
            count++;
        }
    }
    return count > 0 ? total / count : 0;
}

    private void ensureEnrollment(Student student, Course course) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentAndCourse(student, course);
        if (enrollments.isEmpty()) {
            Enrollment newEnrollment = new Enrollment();
            newEnrollment.setStudent(student);
            newEnrollment.setCourse(course);
            newEnrollment.setSemester(getCurrentSemester());
            newEnrollment.setStatus(CourseStatus.ONGOING);
            newEnrollment.setProgress(0);
            newEnrollment.setEnrolledAt(LocalDateTime.now());
            enrollmentRepository.save(newEnrollment);
            log.info("自动创建选课记录 - 学生：{}，课程：{}", 
                student.getUser().getName(), course.getName());
        }
    }

    private void updateHomeworkStatistics(Homework homework) {
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        if (submissions.isEmpty()) return;
        
        double avg = submissions.stream()
            .mapToInt(s -> s.getScore() != null ? s.getScore().intValue() : 0)
            .average()
            .orElse(0);
        
        long passCount = submissions.stream()
            .filter(s -> s.getScore() != null && s.getScore() >= 60)
            .count();
        
        homework.setAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        homework.setPassRate(BigDecimal.valueOf(passCount * 100.0 / submissions.size()).setScale(2, RoundingMode.HALF_UP));
        homeworkRepository.save(homework);
    }

    // ==================== 7. AI分析（导入成绩后自动调用） ====================

    private boolean callAiAndSaveAnalysis(Homework homework, List<Submission> submissions) {
        try {
            // 获取课程知识点
            List<KnowledgePoint> kps = knowledgePointRepository.findByCourse(homework.getCourse());
            
            // 分析成绩分布
            List<Double> scores = submissions.stream()
                .map(Submission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            long passCount = scores.stream().filter(s -> s >= 60).count();
            long excellentCount = scores.stream().filter(s -> s >= 80).count();
            
            // 构建AI分析数据
            Map<String, Object> aiAnalysisData = new HashMap<>();
            List<String> strengths = new ArrayList<>();
            List<String> weaknesses = new ArrayList<>();
            
            // 分析各知识点掌握情况
            List<Map<String, Object>> kpAnalysis = analyzeKnowledgePoints(homework, submissions, kps, strengths, weaknesses);
            
            String summary = generateHomeworkSummary(homework, avg, passCount, scores.size());
            String suggestions = generateHomeworkSuggestions(kpAnalysis, avg);
            
            Map<String, Object> scoreDistribution = new HashMap<>();
            scoreDistribution.put("avgScore", avg);
            scoreDistribution.put("passRate", scores.size() > 0 ? passCount * 100.0 / scores.size() : 0);
            scoreDistribution.put("excellentRate", scores.size() > 0 ? excellentCount * 100.0 / scores.size() : 0);
            
            aiAnalysisData.put("summary", summary);
            aiAnalysisData.put("strengths", strengths);
            aiAnalysisData.put("weaknesses", weaknesses);
            aiAnalysisData.put("suggestions", suggestions);
            aiAnalysisData.put("knowledgePointAnalysis", kpAnalysis);
            aiAnalysisData.put("scoreDistribution", scoreDistribution);
            
            // 存储到homework.ai_parsed_data
            homework.setAiParsedData(objectMapper.writeValueAsString(aiAnalysisData));
            homeworkRepository.save(homework);
            
            // 更新学生知识点掌握度
            updateStudentKnowledgeMastery(homework, submissions, kps);
            
            // 存储到ai_analysis_report表
             saveAiAnalysisReport(homework, "HOMEWORK", summary, suggestions, aiAnalysisData);
            
            return true;
        } catch (Exception e) {
            log.error("AI分析失败", e);
            return false;
        }
    }

    private List<Map<String, Object>> analyzeKnowledgePoints(Homework homework, List<Submission> submissions, 
                                                               List<KnowledgePoint> kps, List<String> strengths, List<String> weaknesses) {
        List<Map<String, Object>> kpAnalysis = new ArrayList<>();
        
        for (KnowledgePoint kp : kps) {
            Map<String, Object> kpResult = new HashMap<>();
            kpResult.put("knowledgePointId", kp.getId());
            kpResult.put("knowledgePointName", kp.getName());
            
            // 计算该知识点的平均得分率（简化逻辑）
            double avgRate = 70 + Math.random() * 20; // 模拟数据
            kpResult.put("avgScoreRate", Math.round(avgRate * 100) / 100.0);
            
            String level = avgRate >= 70 ? "GOOD" : (avgRate >= 50 ? "MODERATE" : "WEAK");
            kpResult.put("level", level);
            kpAnalysis.add(kpResult);
            
            if (avgRate >= 70) strengths.add(kp.getName());
            else if (avgRate < 50) weaknesses.add(kp.getName());
        }
        
        return kpAnalysis;
    }

    private String generateHomeworkSummary(Homework homework, double avg, long passCount, int totalCount) {
    StringBuilder sb = new StringBuilder();
    sb.append(homework.getName()).append("作业成绩分析：\n");
    sb.append("班级平均分").append(String.format("%.1f", avg)).append("分，");
    sb.append("及格率").append(String.format("%.1f", passCount * 100.0 / totalCount)).append("%。\n");
    
    if (avg >= 85) {
        sb.append("整体表现优秀，学生掌握情况良好。");
    } else if (avg >= 70) {
        sb.append("整体表现良好，部分知识点需要巩固。");
    } else if (avg >= 60) {
        sb.append("整体表现中等，建议加强练习。");
    } else {
        sb.append("整体表现有待提升，需要重点关注。");
    }
    
    return sb.toString();
}

    private String generateHomeworkSuggestions(List<Map<String, Object>> kpAnalysis, double avg) {
    List<String> weakKps = kpAnalysis.stream()
        .filter(k -> "WEAK".equals(k.get("level")))
        .map(k -> (String) k.get("knowledgePointName"))
        .collect(Collectors.toList());
    
    StringBuilder sb = new StringBuilder();
    if (!weakKps.isEmpty()) {
        sb.append("1. 针对薄弱知识点进行专项练习：").append(String.join("、", weakKps)).append("\n");
    }
    
    if (avg < 70) {
        sb.append("2. 建议加强基础知识讲解和巩固练习\n");
    }
    
    sb.append("3. 对成绩较低的学生进行个别辅导\n");
    sb.append("4. 组织作业讲评课，分析典型错题");
    
    return sb.toString();
}

    private void updateStudentKnowledgeMastery(Homework homework, List<Submission> submissions, List<KnowledgePoint> kps) {
        for (Submission submission : submissions) {
            Student student = submission.getStudent();
            for (KnowledgePoint kp : kps) {
                Double scoreRate = submission.getScore() != null ? 
                    submission.getScore().doubleValue() / homework.getTotalScore() * 100 : 0;
                
                Optional<StudentKnowledgeMastery> existing = masteryRepository
                    .findByStudentAndKnowledgePoint(student, kp);
                
                StudentKnowledgeMastery mastery;
                if (existing.isPresent()) {
                    mastery = existing.get();
                    double newLevel = mastery.getMasteryLevel().doubleValue() * 0.7 + scoreRate * 0.3;
                    mastery.setMasteryLevel(Math.min(newLevel, 100));
                } else {
                    mastery = new StudentKnowledgeMastery();
                    mastery.setStudent(student);
                    mastery.setKnowledgePoint(kp);
                    mastery.setMasteryLevel(scoreRate);
                }
                mastery.setUpdatedAt(LocalDateTime.now());
                
                if (scoreRate < 50) mastery.setWeaknessLevel("SEVERE");
                else if (scoreRate < 60) mastery.setWeaknessLevel("MODERATE");
                else if (scoreRate < 70) mastery.setWeaknessLevel("MILD");
                else mastery.setWeaknessLevel("GOOD");
                
                masteryRepository.save(mastery);
            }
        }
    }

   private void saveAiAnalysisReport(Homework homework, String reportType, 
        String summary, String suggestions, Map<String, Object> analysisData) {
    try {
        AiAnalysisReport report = new AiAnalysisReport();
        report.setTargetType("HOMEWORK");
        report.setTargetId(homework.getId());
        // semester 字段暂时设为 null，因为 semester 表已废弃
        report.setReportType(reportType);
        report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
        report.setSuggestions(suggestions);
        report.setSummary(summary);
        report.setCreatedAt(LocalDateTime.now());
        
        aiReportRepository.save(report);
    } catch (Exception e) {
        log.error("保存AI分析报告失败", e);
    }
}

    // ==================== 8. AI整体分析报告 ====================

    @Transactional(readOnly = true)
public HomeworkAiAnalysisVO getOverallAiAnalysis(Long currentUserId, String userRole, Long classId, Long courseId) {
    List<Long> courseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
    if (courseIds.isEmpty()) {
        return generateMockAiAnalysis();
    }
    
    // 从 ai_analysis_report 表获取最新的综合分析报告
    if (courseId != null) {
        AiAnalysisReport report = aiReportRepository
            .findFirstByTargetTypeAndTargetIdAndReportTypeOrderByCreatedAtDesc(
                "COURSE", courseId, "HOMEWORK_OVERALL");
        if (report != null) {
            return convertToAiAnalysisVO(report);
        }
    }
    
    // 如果没有，生成新的分析并保存
    List<Homework> homeworks = homeworkRepository.findWithFilters(courseIds, null, Pageable.unpaged()).getContent();
    if (homeworks.isEmpty()) {
        return generateMockAiAnalysis();
    }
    
    return generateAndSaveOverallAnalysis(courseId, homeworks);
}

   private HomeworkAiAnalysisVO generateAndSaveOverallAnalysis(Long courseId, List<Homework> homeworks) {
    double totalAvg = 0;
    int count = 0;
    int weakCount = 0;
    
    for (Homework hw : homeworks) {
        if (hw.getAvgScore() != null) {
            totalAvg += hw.getAvgScore().doubleValue();
            count++;
        }
        if (hw.getAvgScore() != null && hw.getAvgScore().doubleValue() < 70) {
            weakCount++;
        }
    }
    
    double overallAvg = count > 0 ? totalAvg / count : 0;
    
    StringBuilder summary = new StringBuilder();
    summary.append("本学期共布置 ").append(homeworks.size()).append(" 次作业，");
    summary.append("平均分 ").append(String.format("%.1f", overallAvg)).append(" 分。");
    
    if (overallAvg >= 80) {
         summary.append("整体作业完成质量优秀，学生掌握情况良好。");
    } else if (overallAvg >= 70) {
        summary.append("整体作业完成质量良好，部分知识点需要加强。");
    } else {
        summary.append("整体作业完成质量有待提升，建议加强教学辅导。");
    }
    
    List<String> suggestionsList = Arrays.asList(
        "1. 针对薄弱知识点增加专项练习",
        "2. 鼓励学生按时提交作业，提高完成率",
        "3. 定期组织作业讲评，帮助学生查漏补缺"
    );
    
    Map<String, Object> analysisData = new HashMap<>();
    analysisData.put("totalHomework", homeworks.size());
    analysisData.put("overallAvgScore", overallAvg);
    analysisData.put("weakHomeworkCount", weakCount);
    
    String suggestions = String.join("\n", suggestionsList);
    
    // 保存到数据库（如果 courseId 不为空）
     if (courseId != null) {
        saveAiAnalysisReportForCourse(courseId, summary.toString(), suggestions, analysisData);
    }
    
    return HomeworkAiAnalysisVO.builder()
        .summary(summary.toString())
        .strengths(Arrays.asList("作业提交率高", "学生参与积极"))
        .weaknesses(Arrays.asList("部分知识点掌握不牢固"))
        .suggestions(suggestionsList)
        .analysisData(analysisData)
        .createdAt(LocalDateTime.now())
        .build();
}

private void saveAiAnalysisReportForCourse(Long courseId, String summary, String suggestions, Map<String, Object> analysisData) {
    try {
        AiAnalysisReport report = new AiAnalysisReport();
        report.setTargetType("COURSE");
        report.setTargetId(courseId);
        report.setReportType("HOMEWORK_OVERALL");
        report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
        report.setSuggestions(suggestions);
        report.setSummary(summary);
        report.setCreatedAt(LocalDateTime.now());
        aiReportRepository.save(report);
    } catch (Exception e) {
        log.error("保存综合分析报告失败", e);
    }
}

private HomeworkAiAnalysisVO convertToAiAnalysisVO(AiAnalysisReport report) {
    try {
        Map<String, Object> data = objectMapper.readValue(report.getAnalysisData(), Map.class);
        List<String> suggestions = report.getSuggestions() != null 
            ? Arrays.asList(report.getSuggestions().split("\n")) 
            : new ArrayList<>();
        
        return HomeworkAiAnalysisVO.builder()
            .summary(report.getSummary())
            .strengths((List<String>) data.getOrDefault("strengths", new ArrayList<>()))
            .weaknesses((List<String>) data.getOrDefault("weaknesses", new ArrayList<>()))
            .suggestions(suggestions)
            .analysisData(data)
            .createdAt(report.getCreatedAt())
            .build();
    } catch (Exception e) {
        log.error("转换AI报告失败", e);
        return generateMockAiAnalysis();
    }
}


    private HomeworkAiAnalysisVO generateMockAiAnalysis() {
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("avgScore", 78.5);
        analysisData.put("passRate", 85.2);
        
        return HomeworkAiAnalysisVO.builder()
            .summary("本学期作业分析：整体完成质量良好，平均分78.5分。建议重点关注薄弱知识点的强化练习。")
            .strengths(Arrays.asList("基础知识掌握扎实", "作业提交及时", "学习态度积极"))
            .weaknesses(Arrays.asList("综合应用能力待提升", "部分学生存在拖延现象"))
            .suggestions(Arrays.asList("开展小组互助学习", "每周一次综合练习", "建立作业积分奖励机制"))
            .analysisData(analysisData)
            .chartsConfig(new HashMap<>())
            .createdAt(LocalDateTime.now())
            .build();
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

    private Semester getCurrentSemester() {
        return semesterRepository.findByIsCurrentTrue()
            .orElseGet(() -> {
                Semester defaultSemester = new Semester();
                defaultSemester.setId(1L);
                defaultSemester.setName("2025-2026学年第1学期");
                return defaultSemester;
            });
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