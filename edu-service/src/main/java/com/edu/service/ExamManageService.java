package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamManageService {

    private final ExamRepository examRepository;
    private final ExamGradeRepository examGradeRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final AiAnalysisReportRepository aiReportRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;
    private final ObjectMapper objectMapper;
    
    // 临时文件存储
    private final Map<String, ExamImportPreviewVO> tempFileStore = new HashMap<>();

    // ==================== 权限相关方法 ====================

    private List<Long> getVisibleClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) {
                return Arrays.asList(requestClassId);
            }
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

    public List<ClassInfo> getTeacherClasses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) return new ArrayList<>();
        return classRepository.findByTeacher(teacher);
    }

    public List<Course> getTeacherCourses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) return new ArrayList<>();
        return courseRepository.findByTeacher(teacher);
    }

    public List<ClassInfo> getAllClasses() {
        return classRepository.findAll();
    }

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    // ==================== 考试列表相关 ====================

    @Transactional(readOnly = true)
    public Page<ExamInfoVO> getExamList(ExamListRequest request, Long currentUserId, String userRole) {
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, request.getClassId());
        if (visibleClassIds.isEmpty()) return Page.empty();
        
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        Page<Exam> examPage;
        if (request.getCourseId() != null) {
            examPage = getExamsByCourse(request.getCourseId(), request.getKeyword(), pageable, visibleClassIds);
        } else {
            examPage = getExamsByClasses(visibleClassIds, request.getKeyword(), pageable);
        }
        
        List<ExamInfoVO> voList = examPage.getContent().stream()
            .map(this::convertToExamInfoVO)
            .collect(Collectors.toList());
        
        return new PageImpl<>(voList, pageable, examPage.getTotalElements());
    }

    private Page<Exam> getExamsByCourse(Long courseId, String keyword, Pageable pageable, List<Long> visibleClassIds) {
        Page<Exam> examPage;
        if (keyword != null && !keyword.isEmpty()) {
            examPage = examRepository.findByCourseIdAndKeyword(courseId, keyword, pageable);
        } else {
            examPage = examRepository.findByCourseId(courseId, pageable);
        }
        List<Exam> filtered = examPage.getContent().stream()
            .filter(e -> visibleClassIds.contains(e.getClassInfo().getId()))
            .collect(Collectors.toList());
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    private Page<Exam> getExamsByClasses(List<Long> classIds, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return examRepository.findByClassIdsAndKeyword(classIds, keyword, pageable);
        }
        return examRepository.findByClassIds(classIds, pageable);
    }

    private ExamInfoVO convertToExamInfoVO(Exam exam) {
         // 检查 classInfo 是否为 null
    if (exam.getClassInfo() == null) {
        throw new RuntimeException("考试未关联班级");
    }
        List<ExamGrade> grades = examGradeRepository.findByExam(exam);
        int studentCount = grades.size();
        double avg = grades.stream().mapToDouble(g -> g.getScore().doubleValue()).average().orElse(0);
        long passCount = grades.stream().filter(g -> g.getScore().doubleValue() >= exam.getPassScore()).count();
        
         boolean hasAiAnalysis = false;
        try {
            // 使用统一AI服务生成考试分析报告
            unifiedAiAnalysisService.getOrCreateAnalysis(
                "EXAM",
                exam.getId(),
                "EXAM_ANALYSIS",
                false
                );
                hasAiAnalysis = true;
                log.info("考试AI分析完成，考试ID: {}", exam.getId());
            } catch (Exception e) {
                log.error("考试AI分析失败", e);
                hasAiAnalysis = false;
            }

        return ExamInfoVO.builder()
            .id(exam.getId())
            .name(exam.getName())
            .type(exam.getType())
            .typeText(getExamTypeText(exam.getType()))
            .className(exam.getClassInfo().getName())
            .courseName(exam.getCourse().getName())
            .examDate(exam.getExamDate())
            .fullScore(exam.getFullScore())
            .passScore(exam.getPassScore())
            .status(exam.getStatus())
            .statusText(getExamStatusText(exam.getStatus()))
            .studentCount(studentCount)
            .avgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP))
            .passRate(studentCount == 0 ? BigDecimal.ZERO : 
                BigDecimal.valueOf(passCount * 100.0 / studentCount).setScale(2, RoundingMode.HALF_UP))
            .highestScore(exam.getHighestScore())
            .createdAt(exam.getCreatedAt())
            .hasAiAnalysis(hasAiAnalysis)
            .courseId(exam.getClassInfo().getId())
            .classId(exam.getClassInfo().getId())
            .description(exam.getDescription())
            .build();
    }

     /**
     * 编辑考试
     */
    @Transactional
    public Exam updateExam(Long examId, ExamCreateRequest request) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("考试不存在"));
        
        // 更新基本信息
        exam.setName(request.getName());
        exam.setType(request.getType());
         if (request.getExamDate() != null) exam.setExamDate(request.getExamDate());
        exam.setFullScore(request.getFullScore() != null ? request.getFullScore() : 100);
        exam.setPassScore(request.getPassScore() != null ? request.getPassScore() : 60);
        exam.setDescription(request.getDescription());
        
        // 如果班级有变化，更新班级
    if (request.getClassId() != null && (exam.getClassInfo() == null || !request.getClassId().equals(exam.getClassInfo().getId()))) {
        ClassInfo classInfo = classRepository.findById(request.getClassId())
            .orElseThrow(() -> new RuntimeException("班级不存在"));
        exam.setClassInfo(classInfo);
    }
    
    // 如果课程有变化，更新课程
    if (request.getCourseId() != null && (exam.getCourse() == null || !request.getCourseId().equals(exam.getCourse().getId()))) {
        Course course = courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        exam.setCourse(course);
    }

     if (request.getKnowledgePointIds() != null) {
        exam.setKnowledgePointIds(request.getKnowledgePointIds());
        log.info("更新考试知识点ID列表: {}", request.getKnowledgePointIds());
    }
    
        
        return examRepository.save(exam);
    }

    // ==================== 统计卡片 ====================

    @Transactional(readOnly = true)
    public ExamManageStatsVO getStats(Long currentUserId, String userRole, Long classId, Long courseId) {
        List<Long> classIds = getVisibleClassIds(currentUserId, userRole, classId);
        if (classIds.isEmpty()) return buildEmptyStats();
        
        List<Exam> exams = new ArrayList<>();
        for (Long cid : classIds) {
            ClassInfo classInfo = classRepository.findById(cid).orElse(null);
            if (classInfo != null) exams.addAll(examRepository.findByClassInfo(classInfo));
        }
        
        if (courseId != null) {
            exams = exams.stream().filter(e -> e.getCourse().getId().equals(courseId)).collect(Collectors.toList());
        }
        
        long totalExamCount = exams.size();
        long completedCount = exams.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
        long upcomingCount = exams.stream().filter(e -> "UPCOMING".equals(e.getStatus())).count();
        long ongoingCount = exams.stream().filter(e -> "ONGOING".equals(e.getStatus())).count();
        
        double totalScore = 0;
        int scoreCount = 0;
        int passCount = 0;
        int excellentCount = 0;
        Set<Long> studentSet = new HashSet<>();
        
        for (Exam exam : exams) {
            List<ExamGrade> grades = examGradeRepository.findByExam(exam);
            for (ExamGrade grade : grades) {
                if (grade.getScore() != null) {
                    double score = grade.getScore().doubleValue();
                    totalScore += score;
                    scoreCount++;
                    if (score >= exam.getPassScore()) passCount++;
                    if (score >= 80) excellentCount++;
                    studentSet.add(grade.getStudent().getId());
                }
            }
        }
        
        return ExamManageStatsVO.builder()
            .totalExamCount(totalExamCount)
            .overallAvgScore(scoreCount == 0 ? BigDecimal.ZERO : 
                BigDecimal.valueOf(totalScore / scoreCount).setScale(2, RoundingMode.HALF_UP))
            .overallPassRate(scoreCount == 0 ? BigDecimal.ZERO : 
                BigDecimal.valueOf(passCount * 100.0 / scoreCount).setScale(2, RoundingMode.HALF_UP))
            .overallExcellentRate(scoreCount == 0 ? BigDecimal.ZERO : 
                BigDecimal.valueOf(excellentCount * 100.0 / scoreCount).setScale(2, RoundingMode.HALF_UP))
            .totalStudentCount(studentSet.size())
            .completedCount(completedCount)
            .upcomingCount(upcomingCount)
            .ongoingCount(ongoingCount)
            .build();
    }

    private ExamManageStatsVO buildEmptyStats() {
        return ExamManageStatsVO.builder()
            .totalExamCount(0L).overallAvgScore(BigDecimal.ZERO)
            .overallPassRate(BigDecimal.ZERO).overallExcellentRate(BigDecimal.ZERO)
            .totalStudentCount(0).completedCount(0L).upcomingCount(0L).ongoingCount(0L)
            .build();
    }

    // ==================== 创建考试 ====================

    @Transactional
    public Exam createExam(ExamCreateRequest request) {
        ClassInfo classInfo = classRepository.findById(request.getClassId())
            .orElseThrow(() -> new RuntimeException("班级不存在"));
        Course course = courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        Exam exam = new Exam();
        exam.setName(request.getName());
        exam.setType(request.getType());
        exam.setClassInfo(classInfo);
        exam.setCourse(course);
        exam.setExamDate(request.getExamDate());
        exam.setFullScore(request.getFullScore() != null ? request.getFullScore() : 100);
        exam.setPassScore(request.getPassScore() != null ? request.getPassScore() : 60);
        exam.setDescription(request.getDescription());
        exam.setStatus(ExamStatus.UPCOMING);
        if (request.getKnowledgePointIds() != null && !request.getKnowledgePointIds().isEmpty()) {
        exam.setKnowledgePointIds(request.getKnowledgePointIds());
        log.info("创建考试时保存知识点ID列表: {}", request.getKnowledgePointIds());
    }
        return examRepository.save(exam);
    }

    private Map<String, Object> initKnowledgePointsDistribution(Course course) {
        List<KnowledgePoint> kps = knowledgePointRepository.findByCourse(course);
        Map<String, Object> distribution = new HashMap<>();
        List<Map<String, Object>> kpList = new ArrayList<>();
        for (KnowledgePoint kp : kps) {
            Map<String, Object> kpMap = new HashMap<>();
            kpMap.put("id", kp.getId());
            kpMap.put("name", kp.getName());
            kpMap.put("fullScore", 0);
            kpMap.put("description", kp.getDescription());
            kpList.add(kpMap);
        }
        distribution.put("knowledgePoints", kpList);
        distribution.put("totalScore", 0);
        return distribution;
    }

    // ==================== 考试详情 ====================

   @Transactional(readOnly = true)
public ExamDetailVO getExamDetail(Long examId) {
    Exam exam = examRepository.findById(examId)
        .orElseThrow(() -> new RuntimeException("考试不存在"));
    
    List<ExamGrade> grades = examGradeRepository.findStudentGradesByExamId(examId);
    int totalStudents = getTotalStudentsByClassId(exam.getClassInfo().getId());
    
    if (exam.getClassInfo() == null) {
        log.error("考试 {} 未关联班级", examId);
        throw new RuntimeException("考试未关联班级，请先为考试关联班级");
    }
        
     ExamDetailStatsVO stats = buildExamStats(exam, grades, totalStudents);
    ScoreDistributionDTO distribution = buildScoreDistribution(grades);
    List<ExamStudentGradeVO> studentGrades = buildStudentGrades(grades);
    List<ExamKnowledgePointDTO> knowledgePointAnalysis = buildKnowledgePointAnalysis(exam);
    
    // 修改：从 ai_analysis_report 表获取 AI 分析
    ExamAiAnalysisVO aiAnalysis = parseAiAnalysis(exam);
    
    // 知识点分布已废弃，返回空Map
    Map<String, Object> kpDistribution = new HashMap<>();
    kpDistribution.put("knowledgePoints", new ArrayList<>());
    kpDistribution.put("totalScore", exam.getFullScore());

       return ExamDetailVO.builder()
        .id(exam.getId()).name(exam.getName()).type(exam.getType())
        .typeText(getExamTypeText(exam.getType()))
        .className(exam.getClassInfo().getName())
        .courseName(exam.getCourse().getName()).courseId(exam.getCourse().getId())
        .examDate(exam.getExamDate()).startTime(exam.getStartTime()).endTime(exam.getEndTime())
        .duration(exam.getDuration()).fullScore(exam.getFullScore()).passScore(exam.getPassScore())
        .location(exam.getLocation()).status(exam.getStatus().toString()).description(exam.getDescription())
        .createdAt(exam.getCreatedAt())
        .classAvgScore(exam.getClassAvgScore()).highestScore(exam.getHighestScore()).lowestScore(exam.getLowestScore())
        .stats(stats).scoreDistribution(distribution)
        .studentGrades(studentGrades).knowledgePointAnalysis(knowledgePointAnalysis)
        .aiAnalysis(aiAnalysis).knowledgePointsDistribution(kpDistribution)
        .build();
}

/**
 * 构建成绩分布
 */
private ScoreDistributionDTO buildScoreDistribution(List<ExamGrade> grades) {
    ScoreDistributionDTO distribution = new ScoreDistributionDTO();
    distribution.setExcellentCount(0);
    distribution.setGoodCount(0);
    distribution.setMediumCount(0);
    distribution.setPassCount(0);
    distribution.setFailCount(0);
    
    if (grades == null || grades.isEmpty()) {
        distribution.setAverageScore(BigDecimal.ZERO);
        distribution.setHighestScore(BigDecimal.ZERO);
        distribution.setLowestScore(BigDecimal.ZERO);
        return distribution;
    }
    
    for (ExamGrade grade : grades) {
        int score = grade.getScore() != null ? grade.getScore() : 0;
        if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
 else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
        else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
        else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
        else distribution.setFailCount(distribution.getFailCount() + 1);
    }
    
    double avg = grades.stream()
        .mapToInt(g -> g.getScore() != null ? g.getScore() : 0)
        .average()
        .orElse(0);
    int max = grades.stream()
        .mapToInt(g -> g.getScore() != null ? g.getScore() : 0)
        .max()
        .orElse(0);
    int min = grades.stream()
        .mapToInt(g -> g.getScore() != null ? g.getScore() : 0)
        .min()
        .orElse(0);
 distribution.setAverageScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
    distribution.setHighestScore(BigDecimal.valueOf(max));
    distribution.setLowestScore(BigDecimal.valueOf(min));
    
    return distribution;
}
    private ExamDetailStatsVO buildExamStats(Exam exam, List<ExamGrade> grades, int totalStudents) {
        if (grades.isEmpty()) {
            return ExamDetailStatsVO.builder().totalStudents(totalStudents).submittedCount(0)
                .avgScore(BigDecimal.ZERO).highestScore(BigDecimal.ZERO).lowestScore(BigDecimal.ZERO)
                .passRate(BigDecimal.ZERO).excellentRate(BigDecimal.ZERO).build();
        }
        
        List<Double> scores = grades.stream()
            .map(g -> g.getScore().doubleValue())
            .sorted()
            .collect(Collectors.toList());
        
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double highest = scores.stream().max(Double::compareTo).orElse(0.0);
        double lowest = scores.stream().min(Double::compareTo).orElse(0.0);
        long passCount = scores.stream().filter(s -> s >= exam.getPassScore()).count();
        long excellentCount = scores.stream().filter(s -> s >= 80).count();
        
        double median;
        int size = scores.size();
        if (size % 2 == 0) median = (scores.get(size / 2 - 1) + scores.get(size / 2)) / 2;
        else median = scores.get(size / 2);
        
        double variance = scores.stream().mapToDouble(s -> Math.pow(s - avg, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        return ExamDetailStatsVO.builder()
            .totalStudents(totalStudents).submittedCount(grades.size())
            .avgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP))
            .highestScore(BigDecimal.valueOf(highest)).lowestScore(BigDecimal.valueOf(lowest))
            .passRate(BigDecimal.valueOf(passCount * 100.0 / grades.size()).setScale(2, RoundingMode.HALF_UP))
            .excellentRate(BigDecimal.valueOf(excellentCount * 100.0 / grades.size()).setScale(2, RoundingMode.HALF_UP))
            .medianScore(BigDecimal.valueOf(median).setScale(2, RoundingMode.HALF_UP))
            .standardDeviation(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP))
            .build();
    }

    private List<ExamStudentGradeVO> buildStudentGrades(List<ExamGrade> grades) {
        List<ExamStudentGradeVO> result = new ArrayList<>();
        int rank = 1;
        for (ExamGrade grade : grades) {
            Student student = grade.getStudent();
            User user = student.getUser();
            result.add(ExamStudentGradeVO.builder()
                .studentId(student.getId()).studentNo(student.getStudentNo())
                .studentName(user != null ? user.getName() : "")
                .score(BigDecimal.valueOf(grade.getScore())).classRank(rank++).remark(grade.getRemark())
                .build());
        }
        return result;
    }

   /**
 * 构建知识点分析（修改为从 knowledge_point_score_detail 表实时计算）
 */
private List<ExamKnowledgePointDTO> buildKnowledgePointAnalysis(Exam exam) {
    List<ExamKnowledgePointDTO> result = new ArrayList<>();
    
    // 从考试关联的知识点ID列表获取
    List<Long> knowledgePointIds = exam.getKnowledgePointIds();
    if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
        log.warn("考试 {} 未关联知识点", exam.getId());
        return result;
    }
    
    // 获取知识点信息
    List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
    for (KnowledgePoint kp : kps) {
        // 从 knowledge_point_score_detail 表获取班级平均得分率
        BigDecimal classAvgRate = getKnowledgePointClassAvgScore(exam.getId(), kp.getId());
        
        String level;
        String suggestion;
        if (classAvgRate.doubleValue() >= 70) {
            level = "GOOD";
            suggestion = "🟢 该知识点掌握良好，得分率" + classAvgRate + "%，继续保持";
        } else if (classAvgRate.doubleValue() >= 50) {
            level = "MODERATE";
            suggestion = "🟡 该知识点掌握中等，得分率" + classAvgRate + "%，需加强练习";
        } else {
            level = "WEAK";
            suggestion = "🔴 该知识点班级掌握薄弱，得分率仅" + classAvgRate + "%，建议安排专项复习";
        }
        
        result.add(ExamKnowledgePointDTO.builder()
            .knowledgePointId(kp.getId())
            .knowledgePointName(kp.getName())
            .fullScore(10)  // 满分10分制，可根据需要调整
            .classAvgRate(classAvgRate)
            .level(level)
            .suggestion(suggestion)
            .build());
    }
    
    result.sort((a, b) -> a.getClassAvgRate().compareTo(b.getClassAvgRate()));
    return result;
}
    /**
 * 获取知识点在考试中的班级平均得分（从 knowledge_point_score_detail 表）
 */
private BigDecimal getKnowledgePointClassAvgScore(Long examId, Long knowledgePointId) {
    List<KnowledgePointScoreDetail> details = kpScoreDetailRepository
        .findBySourceTypeAndSourceIdAndKnowledgePointId("EXAM", examId, knowledgePointId);
    
    if (details == null || details.isEmpty()) {
        return BigDecimal.ZERO;
    }
    
    double avg = details.stream()
        .filter(d -> d != null && d.getScoreRate() != null)
        .mapToDouble(d -> d.getScoreRate().doubleValue())
        .average()
        .orElse(0);
    
    return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
}
    private String generateKpSuggestion(String level, BigDecimal rate) {
        if ("WEAK".equals(level)) {
            return "🔴 该知识点班级掌握薄弱，得分率仅" + rate + "%，建议安排专项复习";
        } else if ("MODERATE".equals(level)) {
            return "🟡 该知识点掌握中等，得分率" + rate + "%，需加强练习";
        }
        return "🟢 该知识点掌握良好，得分率" + rate + "%，继续保持";
    }

   /**
 * 解析AI分析数据（从 ai_analysis_report 表获取）
 */
private ExamAiAnalysisVO parseAiAnalysis(Exam exam) {
    if (exam == null || exam.getId() == null) return null;
    
     try {
        // 使用统一AI服务获取考试分析报告
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
            "EXAM",                    // targetType
            exam.getId(),              // targetId
            "EXAM_ANALYSIS",           // reportType
            false                      // 不强制刷新，使用缓存
        );
        
        if (suggestion == null) {
            log.debug("未找到考试AI分析报告，考试ID: {}", exam.getId());
            return null;
        }
        
        // 构建分析数据Map
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("summary", suggestion.getSummary());
        analysisData.put("strengths", suggestion.getStrengths());
        analysisData.put("weaknesses", suggestion.getWeaknesses());
        analysisData.put("suggestions", suggestion.getSuggestions());
        
        return ExamAiAnalysisVO.builder()
            .summary(suggestion.getSummary() != null ? suggestion.getSummary() : "")
            .strengths(suggestion.getStrengths() != null ? suggestion.getStrengths() : new ArrayList<>())
            .weaknesses(suggestion.getWeaknesses() != null ? suggestion.getWeaknesses() : new ArrayList<>())
            .suggestions(suggestion.getSuggestions() != null ? suggestion.getSuggestions() : new ArrayList<>())
            .analysisData(analysisData)
            .createdAt(LocalDateTime.now())
            .build();
            
    } catch (Exception e) {
        log.error("获取考试AI分析失败，考试ID: {}", exam.getId(), e);
        return null;
    }
}

    // ==================== 删除考试 ====================

    @Transactional
    public void deleteExam(Long examId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("考试不存在"));
             // 2. 先删除关联的成绩记录
    examGradeRepository.deleteByExam(exam);
        examRepository.delete(exam);
    }

    // ==================== 导入成绩 ====================

    @Transactional
    public ExamImportPreviewVO parseImportFile(MultipartFile file, Long examId) throws Exception {
        String fileName = file.getOriginalFilename();
        String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        
        List<ExamImportRowVO> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        if ("csv".equals(fileExt) || "xlsx".equals(fileExt) || "xls".equals(fileExt)) {
            rows = parseExamFile(file, errors);
        } else if ("txt".equals(fileExt)) {
            rows = parseExamTextFile(file, errors);
        } else {
            throw new Exception("不支持的文件格式，请上传CSV、Excel或TXT文件");
        }
        
        long validRows = rows.stream().filter(ExamImportRowVO::getIsValid).count();
        
        // 调用AI解析（预留）
        Map<String, Object> aiAnalysis = callAiForExamParse(rows);
        
        String fileId = UUID.randomUUID().toString();
        Exam exam = examId != null ? examRepository.findById(examId).orElse(null) : null;
        
        ExamImportPreviewVO preview = ExamImportPreviewVO.builder()
            .fileId(fileId).fileName(fileName).examId(examId)
            .examName(exam != null ? exam.getName() : "")
            .totalRows(rows.size()).validRows((int) validRows).invalidRows(rows.size() - (int) validRows)
            .rows(rows).errors(errors).aiAnalysis(aiAnalysis)
            .build();
        
        tempFileStore.put(fileId, preview);
        scheduleTempFileCleanup(fileId);
        return preview;
    }

    private List<ExamImportRowVO> parseExamFile(MultipartFile file, List<String> errors) {
        List<ExamImportRowVO> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int rowNum = 0;
            while ((line = reader.readLine()) != null && rowNum < 1000) {
                rowNum++;
                if (rowNum == 1 && line.startsWith("学号")) continue;
                
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    rows.add(ExamImportRowVO.builder().rowNum(rowNum).isValid(false)
                        .errorMsg("数据列数不足，需要学号、姓名、成绩").build());
                    continue;
                }
                
                String studentNo = parts[0].trim();
                String studentName = parts[1].trim();
                Integer score;
                try {
                    score = new Integer(parts[2].trim());
                } catch (NumberFormatException e) {
                    rows.add(ExamImportRowVO.builder().rowNum(rowNum).studentNo(studentNo)
                        .studentName(studentName).isValid(false).errorMsg("成绩格式错误").build());
                    continue;
                }
                
                String remark = parts.length > 3 ? parts[3].trim() : "";
                Optional<Student> studentOpt = studentRepository.findByStudentNo(studentNo);
                boolean isValid = studentOpt.isPresent();
                
                rows.add(ExamImportRowVO.builder().rowNum(rowNum).studentNo(studentNo)
                    .studentName(studentName).score(score).remark(remark)
                    .isValid(isValid).errorMsg(isValid ? null : "学号不存在").build());
            }
        } catch (Exception e) {
            errors.add("文件解析失败: " + e.getMessage());
        }
        return rows;
    }

    private List<ExamImportRowVO> parseExamTextFile(MultipartFile file, List<String> errors) {
        List<ExamImportRowVO> rows = new ArrayList<>();
        // TODO: 实现JSON格式解析
        return rows;
    }

    private Map<String, Object> callAiForExamParse(List<ExamImportRowVO> rows) {
        Map<String, Object> result = new HashMap<>();
        result.put("aiEnabled", false);
        result.put("message", "AI解析功能待接入");
        
        List<Integer> scores = rows.stream()
    .filter(ExamImportRowVO::getIsValid)
    .map(ExamImportRowVO::getScore)
    .filter(Objects::nonNull)
    .collect(Collectors.toList());

if (!scores.isEmpty()) {
    double avg = scores.stream()
        .mapToInt(Integer::intValue)  // 使用 mapToInt
        .average()
        .orElse(0);
            result.put("avgScore", Math.round(avg * 100) / 100.0);
            result.put("suggestion", "数据质量良好，确认后可导入");
        }
        return result;
    }

    public ExamImportPreviewVO getFilePreview(String fileId) {
        return tempFileStore.get(fileId);
    }

    public void cancelImport(String fileId) {
        tempFileStore.remove(fileId);
    }

    @Transactional
    public ExamImportResultVO confirmImport(ExamImportConfirmRequest request) {
        ExamImportPreviewVO preview = tempFileStore.get(request.getFileId());
        if (preview == null) throw new RuntimeException("文件预览已过期，请重新上传");
        
        Exam exam = examRepository.findById(request.getExamId())
            .orElseThrow(() -> new RuntimeException("考试不存在"));
        
        List<ExamImportRowVO> rowsToImport = new ArrayList<>();
        if (request.getSelectedRowIndexes() != null && !request.getSelectedRowIndexes().isEmpty()) {
            for (int idx : request.getSelectedRowIndexes()) {
                if (idx >= 0 && idx < preview.getRows().size())
                    rowsToImport.add(preview.getRows().get(idx));
            }
        } else {
            rowsToImport = preview.getRows().stream().filter(ExamImportRowVO::getIsValid).collect(Collectors.toList());
        }
        
        int successCount = 0, failCount = 0;
        List<String> errors = new ArrayList<>();
        List<ExamGrade> savedGrades = new ArrayList<>();
        
        for (ExamImportRowVO row : rowsToImport) {
            try {
                ExamGrade grade = importExamGrade(exam, row);
                savedGrades.add(grade);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("第" + row.getRowNum() + "行导入失败: " + e.getMessage());
            }
        }
        
        // 更新考试统计数据
        updateExamStatistics(exam);
        
       // 调用统一AI服务生成分析报告
        boolean aiCompleted = false;
        try {
            // 使用统一AI服务生成考试分析报告
            unifiedAiAnalysisService.getOrCreateAnalysis(
                "EXAM",
                exam.getId(),
                "EXAM_ANALYSIS",
                true  // 强制刷新，因为刚导入新数据
            );
            aiCompleted = true;
            log.info("考试AI分析完成，考试ID: {}", exam.getId());
        } catch (Exception e) {
            log.error("考试AI分析失败", e);
            aiCompleted = false;
        }
        
        tempFileStore.remove(request.getFileId());
        
        return ExamImportResultVO.builder()
            .examId(exam.getId()).examName(exam.getName())
            .totalImported(rowsToImport.size()).successCount(successCount).failCount(failCount)
            .errors(errors).aiAnalysisCompleted(aiCompleted)
            .build();
    }

    private ExamGrade importExamGrade(Exam exam, ExamImportRowVO row) {
        Student student = studentRepository.findByStudentNo(row.getStudentNo())
            .orElseThrow(() -> new RuntimeException("学生不存在: " + row.getStudentNo()));
        
        Optional<ExamGrade> existing = examGradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId());
        ExamGrade grade;
        if (existing.isPresent()) {
            grade = existing.get();
            grade.setScore(row.getScore());
            grade.setRemark(row.getRemark());
        } else {
            grade = new ExamGrade();
            grade.setExam(exam);
            grade.setStudent(student);
            grade.setScore(row.getScore());
            grade.setRemark(row.getRemark());
            grade.setCreatedAt(LocalDateTime.now());
        }
        
        // 计算班级排名
        List<ExamGrade> allGrades = examGradeRepository.findByExam(exam);
        allGrades.add(grade);
        allGrades.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        for (int i = 0; i < allGrades.size(); i++) {
            if (allGrades.get(i).getStudent().getId().equals(student.getId())) {
                grade.setClassRank(i + 1);
                break;
            }
        }
        
        return examGradeRepository.save(grade);
    }

    private void updateExamStatistics(Exam exam) {
        List<ExamGrade> grades = examGradeRepository.findByExam(exam);
        if (grades.isEmpty()) return;
        
        double avg = grades.stream().mapToDouble(g -> g.getScore().doubleValue()).average().orElse(0);
        double highest = grades.stream().mapToDouble(g -> g.getScore().doubleValue()).max().orElse(0);
        double lowest = grades.stream().mapToDouble(g -> g.getScore().doubleValue()).min().orElse(0);
        
        exam.setClassAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        exam.setHighestScore(BigDecimal.valueOf(highest));
        exam.setLowestScore(BigDecimal.valueOf(lowest));
        exam.setStatus(ExamStatus.COMPLETED);
        examRepository.save(exam);
    }

    private int getTotalStudentsByClassId(Long classId) {
        ClassInfo classInfo = classRepository.findById(classId).orElse(null);
        if (classInfo == null) return 0;
        return (int) studentRepository.countByClassInfo(classInfo);
    }

     /**
     * 获取课程考试AI报告（迁移到统一服务）
     */
    @Transactional(readOnly = false)
    public ExamAiAnalysisVO getCourseExamAiReport(Long courseId, Long classId) {
        // 根据参数确定目标类型
        String targetType = classId != null ? "CLASS" : "COURSE";
        Long targetId = classId != null ? classId : courseId;
        
        if (targetId == null) {
            return generateMockAiAnalysis();
        } // 使用统一服务
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
            targetType,
            targetId,
            "EXAM_OVERALL",  // 考试整体分析
            false
        );
        
        // 转换为 ExamAiAnalysisVO
        return convertToExamAiAnalysisVO(suggestion);
    }

     /**
     * 转换为 ExamAiAnalysisVO
     */
    private ExamAiAnalysisVO convertToExamAiAnalysisVO(AiSuggestionDTO suggestion) {
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("summary", suggestion.getSummary());
        analysisData.put("strengths", suggestion.getStrengths());
        analysisData.put("weaknesses", suggestion.getWeaknesses());
        
        return ExamAiAnalysisVO.builder()
            .summary(suggestion.getSummary())
            .strengths(suggestion.getStrengths())
            .weaknesses(suggestion.getWeaknesses())
            .suggestions(suggestion.getSuggestions())
            .analysisData(analysisData)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private ExamAiAnalysisVO generateMockAiAnalysis() {
      Map<String, Object> analysisData = new HashMap<>();
      analysisData.put("avgScore", 78.5);
      analysisData.put("passRate", 85.2);

      Map<String, Object> chartsConfig = new HashMap<>();
      chartsConfig.put("type", "line");
      chartsConfig.put("title", "成绩趋势");
              return ExamAiAnalysisVO.builder()
            .summary("本学期考试分析：整体成绩稳步提升，期末平均分较期中提高3.2分。建议继续保持当前教学节奏，重点关注中等生转化。")
            .strengths(Arrays.asList("基础知识扎实", "学习氛围良好", "作业完成率高"))
            .weaknesses(Arrays.asList("综合应用能力待提升", "部分学生存在偏科现象"))
            .suggestions(Arrays.asList("开展小组互助学习", "每周一次综合练习", "建立培优补差机制"))
            
            .analysisData(analysisData)
            .chartsConfig(chartsConfig)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void scheduleTempFileCleanup(String fileId) {
        new Thread(() -> {
            try {
                Thread.sleep(30 * 60 * 1000);
                tempFileStore.remove(fileId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private String getExamTypeText(ExamStatus type) {
        Map<String, String> map = new HashMap<>();
          map.put("MOCK", "模拟考");
          map.put("UNIT", "单元测试");
          map.put("MONTHLY", "月考");
          map.put("MIDTERM", "期中考试");
          map.put("FINAL", "期末考试");
    return map.getOrDefault(type.toString(), type.toString());
    }

    private String getExamStatusText(ExamStatus status) {
         Map<String, String> statusMap = new HashMap<>();
        statusMap.put("UPCOMING", "即将开始");
        statusMap.put("ONGOING", "进行中");
        statusMap.put("COMPLETED", "已完成");
        return statusMap.getOrDefault(status.toString(), status.toString());
    }

/**
 * 分页获取考试的学生成绩列表
 */
@Transactional(readOnly = true)
public ExamStudentGradePageVO getStudentGradesPage(ExamStudentGradePageRequest request, 
                                                    Long currentUserId, 
                                                    String userRole, 
                                                    Long examId) {
    // 检查考试是否存在
    Exam exam = examRepository.findById(examId)
        .orElseThrow(() -> new RuntimeException("考试不存在"));
    
    // 构建分页参数
    int page = request.getPage() != null ? request.getPage() : 0;
    int size = request.getSize() != null ? request.getSize() : 10;
    
    Sort sort = buildSort(request.getSortBy(), request.getSortOrder());
    Pageable pageable = PageRequest.of(page, size, sort);
    
    // 查询数据
    Page<ExamGrade> gradePage;
    if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
        gradePage = examGradeRepository.findByExamIdAndKeyword(examId, request.getKeyword(), pageable);
    } else {
        gradePage = examGradeRepository.findByExamIdWithStudent(examId, pageable);
    }
    
    // 转换为VO
    List<ExamStudentGradeVO> records = gradePage.getContent().stream()
        .map(this::convertToExamStudentGradeVO)
        .collect(Collectors.toList());
    
    // 获取统计信息
    ExamStudentGradePageVO.Statistics statistics = getGradeStatistics(exam);
    
    return ExamStudentGradePageVO.builder()
        .records(records)
        .total(gradePage.getTotalElements())
        .current(page)
        .size(size)
        .pages(gradePage.getTotalPages())
        .statistics(statistics)
        .build();
}

/**
 * 构建排序
 */
private Sort buildSort(String sortBy, String sortOrder) {
    String field = sortBy != null ? sortBy : "score";
    boolean isDesc = !"asc".equalsIgnoreCase(sortOrder);
    
    // 处理关联字段排序
    switch (field) {
        case "studentNo":
            field = "student.studentNo";
            break;
        case "studentName":
            field = "student.user.name";
            break;
        case "classRank":
            field = "classRank";
            break;
        case "score":
        default:
            field = "score";
            break;
    }
    
    return isDesc ? Sort.by(field).descending() : Sort.by(field).ascending();
}

/**
 * 转换 ExamGrade 到 ExamStudentGradeVO
 */
private ExamStudentGradeVO convertToExamStudentGradeVO(ExamGrade grade) {
    Student student = grade.getStudent();
    User user = student.getUser();
    
    return ExamStudentGradeVO.builder()
        .studentId(student.getId())
        .studentNo(student.getStudentNo())
        .studentName(user != null ? user.getName() : "")
        .score(grade.getScore() != null ? BigDecimal.valueOf(grade.getScore()) : null)
        .classRank(grade.getClassRank())
        .remark(grade.getRemark())
        .scoreTrend(grade.getScoreTrend())
        .build();
}

/**
 * 获取成绩统计信息
 */
private ExamStudentGradePageVO.Statistics getGradeStatistics(Exam exam) {
    if (exam == null) {
        return ExamStudentGradePageVO.Statistics.builder()
            .totalStudents(0).avgScore(0.0).highestScore(0.0).lowestScore(0.0)
            .passRate(0.0).excellentRate(0.0).passCount(0).excellentCount(0).failCount(0)
            .build();
    }
    
    Object[] result  = examGradeRepository.getScoreStatistics(exam.getId());

    if (result  == null || result.length ==0) {
        return ExamStudentGradePageVO.Statistics.builder()
            .totalStudents(0).avgScore(0.0).highestScore(0.0).lowestScore(0.0)
            .passRate(0.0).excellentRate(0.0).passCount(0).excellentCount(0).failCount(0)
            .build();
    }
     Object[] stats = (Object[]) result[0];

    // 安全转换
    double avg = 0.0;
    double highest = 0.0;
    double lowest = 0.0;
    Integer total = 0;
    
    try {
        if (stats[0] != null) avg = ((Number) stats[0]).doubleValue();
        if (stats[1] != null) highest = ((Number) stats[1]).doubleValue();
        if (stats[2] != null) lowest = ((Number) stats[2]).doubleValue();
        if (stats[3] != null) total = (Integer)stats[3];
    } catch (Exception e) {
        log.error("解析统计结果失败: {}", e.getMessage());
    }
    

    log.info("获取成绩统计信息: {}", avg,total);
    
    // 统计各分数段人数
    long passCount = examGradeRepository.countByExamIdAndScoreGreaterThanEqual(exam.getId(), (Integer) exam.getPassScore());
    
    return ExamStudentGradePageVO.Statistics.builder()
        .totalStudents((int) total)
        .avgScore(Math.round(avg * 100) / 100.0)
        .highestScore(highest)
        .lowestScore(lowest)
        .passCount((int) passCount)
        .build();
}
}