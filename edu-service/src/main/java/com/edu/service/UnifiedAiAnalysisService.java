package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.*;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedAiAnalysisService {

    private final DeepSeekService deepSeekService;
    private final AiAnalysisReportService aiReportService;
    
    // 数据源Repository
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ExamGradeRepository examGradeRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final ExamRepository examRepository;
    private final HomeworkRepository homeworkRepository;

    /**
     * 统一入口：获取或生成AI分析报告
     * @param targetType 目标类型：STUDENT, CLASS, COURSE, EXAM, HOMEWORK
     * @param targetId 目标ID
     * @param reportType 报告类型：COMPREHENSIVE, EXAM_ANALYSIS, HOMEWORK_ANALYSIS, KNOWLEDGE_ANALYSIS
     * @param forceRefresh 是否强制刷新
     * @return AI分析报告DTO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiSuggestionDTO getOrCreateAnalysis(String targetType, Long targetId, 
                                                String reportType, boolean forceRefresh) {
        log.info("获取AI分析报告: targetType={}, targetId={}, reportType={}, forceRefresh={}", 
            targetType, targetId, reportType, forceRefresh);
        
        // 1. 收集源数据
        Map<String, Object> sourceData = collectSourceData(targetType, targetId, reportType);

        // 【优化】检查数据是否有效（是否有足够的数据进行分析）
    DataValidationResult validationResult = validateDataAvailability(targetType, targetId, reportType, sourceData);
    
    if (!validationResult.isValid()) {
        log.info("数据不足，跳过AI调用: targetType={}, targetId={}, reason={}", 
            targetType, targetId, validationResult.getReason());
        return createNoDataReport(targetType, targetId, reportType, validationResult.getReason());
    }
        
        // 2. 计算数据哈希
        String dataHash = calculateDataHash(sourceData);
        
        // 3. 如果不强制刷新，尝试从缓存获取
        if (!forceRefresh) {
            Optional<AiAnalysisReport> cachedOpt = aiReportService.findLatestReport(targetType, targetId, reportType);
            if (cachedOpt.isPresent()) {
                AiAnalysisReport cached = cachedOpt.get();
                if (dataHash.equals(cached.getDataHash())) {
                    log.info("使用缓存的AI分析报告，targetType={}, targetId={}", targetType, targetId);
                    return convertToAiSuggestionDTO(cached);
                } else {
                    log.info("数据已变化，需要重新生成报告，oldHash={}, newHash={}", cached.getDataHash(), dataHash);
                    // 删除旧的报告（数据已变化）
                    aiReportService.deleteOldReports(targetType, targetId, reportType);
                }
            }
        }
        
        // 4. 调用AI生成新报告
        log.info("调用AI生成新报告: targetType={}, targetId={}", targetType, targetId);
        String dataTypeDesc = getDataTypeDescription(targetType, reportType);
        AiSuggestionDTO aiResponse = deepSeekService.analyzeData(JSON.toJSONString(sourceData), dataTypeDesc);
        
        if (aiResponse == null || aiResponse.getSummary() == null) {
            log.error("AI生成报告失败，返回降级报告");
            return createFallbackReport(targetType, targetId);
        }
        
        // 5. 保存到数据库
        saveReport(targetType, targetId, reportType, dataHash, sourceData, aiResponse);
        
        return aiResponse;
    }
    
    /**
     * 删除并重新生成（强制刷新）
     */
    @Transactional
    public AiSuggestionDTO refreshAnalysis(String targetType, Long targetId, String reportType) {
        // 删除旧报告
        aiReportService.deleteOldReports(targetType, targetId, reportType);
        // 重新生成
        return getOrCreateAnalysis(targetType, targetId, reportType, true);
    }

    /**
     * 收集源数据 - 根据不同类型收集不同的数据
     */
    private Map<String, Object> collectSourceData(String targetType, Long targetId, String reportType) {
    switch (targetType) {
        case "STUDENT":
            return collectStudentData(targetId, reportType);
        case "CLASS":
            return collectClassData(targetId);
        case "COURSE":
            return collectCourseData(targetId);
        case "EXAM":
            return collectExamData(targetId);
        case "HOMEWORK":
            return collectHomeworkData(targetId);
        default:
            throw new RuntimeException("不支持的目标类型: " + targetType);
    }
}
    
    /**
 * 收集学生数据
 * 
 * reportType 支持的类型：
 *   - COMPREHENSIVE: 综合分析（包含考试、作业、知识点、活跃度）
 *   - EXAM_OVERALL: 考试整体分析（所有考试）
 *   - EXAM_ANALYSIS_{examId}: 单次考试分析（如 EXAM_ANALYSIS_123）
 *   - HOMEWORK_OVERALL: 作业整体分析（所有作业）
 *   - HOMEWORK_ANALYSIS_{homeworkId}: 单次作业分析（如 HOMEWORK_ANALYSIS_456）
 *   - KNOWLEDGE_ANALYSIS: 知识点分析
 */
private Map<String, Object> collectStudentData(Long studentId, String reportType) {
    Student student = studentRepository.findById(studentId)
        .orElseThrow(() -> new RuntimeException("学生不存在: " + studentId));
    
    Map<String, Object> data = new HashMap<>();
    data.put("studentId", student.getId());
    data.put("studentName", student.getUser().getName());
    data.put("className", student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班");
    data.put("grade", student.getGrade());
    
    // 判断是否是单次考试分析
    if (reportType != null && reportType.startsWith("EXAM_ANALYSIS_")) {
        // 单次考试分析：提取 examId
        String examIdStr = reportType.substring("EXAM_ANALYSIS_".length());
        Long examId = Long.parseLong(examIdStr);
        collectSingleExamAnalysisData(student, examId, data);
    }
    // 判断是否是单次作业分析
    else if (reportType != null && reportType.startsWith("HOMEWORK_ANALYSIS_")) {
        // 单次作业分析：提取 homeworkId
        String homeworkIdStr = reportType.substring("HOMEWORK_ANALYSIS_".length());
        Long homeworkId = Long.parseLong(homeworkIdStr);
        collectSingleHomeworkAnalysisData(student, homeworkId, data);
    }
    // 考试整体分析
    else if ("EXAM_OVERALL".equals(reportType)) {
        collectExamAnalysisData(student, data);
    }
    // 作业整体分析
    else if ("HOMEWORK_OVERALL".equals(reportType)) {
        collectHomeworkAnalysisData(student, data);
    }
    // 知识点分析
    else if ("KNOWLEDGE_ANALYSIS".equals(reportType)) {
        collectKnowledgeAnalysisData(student, data);
    }
    // 综合分析（默认）
    else {
        collectExamAnalysisData(student, data);
        collectHomeworkAnalysisData(student, data);
        collectKnowledgeAnalysisData(student, data);
        collectActivityData(student, data);
    }
    
    return data;
}

/**
 * 收集单次考试分析数据
 */
private void collectSingleExamAnalysisData(Student student, Long examId, Map<String, Object> data) {
    Exam exam = examRepository.findById(examId)
        .orElseThrow(() -> new RuntimeException("考试不存在: " + examId));
    
    Optional<ExamGrade> gradeOpt = examGradeRepository.findByExamIdAndStudentId(examId, student.getId());
    
    if (!gradeOpt.isPresent()) {
        data.put("status", "未参加该考试");
        return;
    }
    
    ExamGrade grade = gradeOpt.get();
    
    data.put("analysisType", "SINGLE_EXAM");
    data.put("examId", exam.getId());
    data.put("examName", exam.getName());
    data.put("courseName", exam.getCourse().getName());
    data.put("examDate", exam.getExamDate());
    data.put("fullScore", exam.getFullScore());
    data.put("passScore", exam.getPassScore());
    data.put("myScore", grade.getScore());
    data.put("classAvgScore", exam.getClassAvgScore());
    data.put("classRank", grade.getClassRank());
    data.put("scoreTrend", grade.getScoreTrend());
    
    // 知识点得分详情
    if (grade.getKnowledgePointScores() != null && !grade.getKnowledgePointScores().isEmpty()) {
        Map<String, Integer> kpScores = parseKnowledgePointScores(grade.getKnowledgePointScores());
        data.put("knowledgePointScores", kpScores);
        
        // 计算平均得分率
        double avgScoreRate = kpScores.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        data.put("avgKnowledgePointScoreRate", avgScoreRate * 10);  // 转为百分制
    }
    
    // 获取班级平均知识点得分率（用于对比）
    Map<String, BigDecimal> classAvgRates = calculateExamClassAvgRates(exam);
    data.put("classAvgKnowledgePointRates", classAvgRates);
}

/**
 * 计算考试班级平均知识点得分率
 */
private Map<String, BigDecimal> calculateExamClassAvgRates(Exam exam) {
    Map<String, BigDecimal> avgRates = new HashMap<>();
    List<ExamGrade> grades = examGradeRepository.findByExam(exam);
    
    if (grades.isEmpty()) return avgRates;
    
    Map<String, List<Integer>> kpScores = new HashMap<>();
    
    for (ExamGrade g : grades) {
        if (g.getKnowledgePointScores() == null) continue;
        Map<String, Integer> scores = parseKnowledgePointScores(g.getKnowledgePointScores());
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            kpScores.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
    }
    
    for (Map.Entry<String, List<Integer>> entry : kpScores.entrySet()) {
        double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
        avgRates.put(entry.getKey(), BigDecimal.valueOf(avg * 10).setScale(2, RoundingMode.HALF_UP));
    }
    
    return avgRates;
}

/**
 * 收集单次作业分析数据
 */
private void collectSingleHomeworkAnalysisData(Student student, Long homeworkId, Map<String, Object> data) {
    Homework homework = homeworkRepository.findById(homeworkId)
        .orElseThrow(() -> new RuntimeException("作业不存在: " + homeworkId));
    
    Optional<Submission> submissionOpt = submissionRepository.findByStudentIdAndHomeworkId(student.getId(), homeworkId);
    
    if (!submissionOpt.isPresent() || submissionOpt.get().getScore() == null) {
        data.put("status", "作业未提交或未批改");
        return;
    }
    
    Submission submission = submissionOpt.get();
    
    data.put("analysisType", "SINGLE_HOMEWORK");
    data.put("homeworkId", homework.getId());
    data.put("homeworkName", homework.getName());
    data.put("courseName", homework.getCourse().getName());
    data.put("deadline", homework.getDeadline());
    data.put("totalScore", homework.getTotalScore());
    data.put("myScore", submission.getScore());
    data.put("classAvgScore", homework.getAvgScore());
    data.put("isLate", submission.getSubmissionLateMinutes() != null && submission.getSubmissionLateMinutes() > 0);
    data.put("lateMinutes", submission.getSubmissionLateMinutes());
    
    // 知识点得分详情
    if (submission.getKnowledgePointScores() != null && !submission.getKnowledgePointScores().isEmpty()) {
        Map<String, Integer> kpScores = parseKnowledgePointScores(submission.getKnowledgePointScores());
        data.put("knowledgePointScores", kpScores);
        
        // 计算平均得分率
        double avgScoreRate = kpScores.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        data.put("avgKnowledgePointScoreRate", avgScoreRate * 10);
    }
    
    // 获取班级平均知识点得分率
    Map<String, BigDecimal> classAvgRates = calculateHomeworkClassAvgRates(homework);
    data.put("classAvgKnowledgePointRates", classAvgRates);
}

/**
 * 计算作业班级平均知识点得分率
 */
private Map<String, BigDecimal> calculateHomeworkClassAvgRates(Homework homework) {
    Map<String, BigDecimal> avgRates = new HashMap<>();
    List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
    
    if (submissions.isEmpty()) return avgRates;
    
    Map<String, List<Integer>> kpScores = new HashMap<>();
    
    for (Submission sub : submissions) {
        if (sub.getKnowledgePointScores() == null) continue;
        Map<String, Integer> scores = parseKnowledgePointScores(sub.getKnowledgePointScores());
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            kpScores.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
    }
    
    for (Map.Entry<String, List<Integer>> entry : kpScores.entrySet()) {
        double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
        avgRates.put(entry.getKey(), BigDecimal.valueOf(avg * 10).setScale(2, RoundingMode.HALF_UP));
    }
    
    return avgRates;
}

 private Map<String, Integer> parseKnowledgePointScores(String json) {
        Map<String, Integer> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;
        
        try {
            String cleaned = json.replace("{", "").replace("}", "").replace("\"", "");
            String[] pairs = cleaned.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    result.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
        } catch (Exception e) {
            log.error("解析知识点得分失败: {}", json, e);
        }
        return result;
    }



    /**
     * 收集考试成绩数据
     */
    private void collectExamAnalysisData(Student student, Map<String, Object> data) {
        List<ExamGrade> examGrades = examGradeRepository.findByStudent(student);
        if (examGrades.isEmpty()) {
            data.put("examStatus", "暂无考试数据");
            return;
        }
        
        List<Map<String, Object>> exams = new ArrayList<>();
        int totalScore = 0;
        int aboveAvgCount = 0;
        
        for (ExamGrade eg : examGrades) {
            Map<String, Object> exam = new HashMap<>();
            exam.put("examName", eg.getExam().getName());
            exam.put("score", eg.getScore());
            exam.put("fullScore", eg.getExam().getFullScore());
            exam.put("classAvg", eg.getExam().getClassAvgScore());
            exam.put("classRank", eg.getClassRank());
            exam.put("examDate", eg.getExam().getExamDate());
            exams.add(exam);
            totalScore += eg.getScore();
            
            if (eg.getExam().getClassAvgScore() != null && 
                eg.getScore() > eg.getExam().getClassAvgScore().doubleValue()) {
                aboveAvgCount++;
            }
        }
        
        data.put("exams", exams);
        data.put("examCount", exams.size());
        data.put("examAvgScore", exams.isEmpty() ? 0 : totalScore * 1.0 / exams.size());
        data.put("aboveClassAvgCount", aboveAvgCount);
    }
    
    /**
     * 收集作业数据
     */
    private void collectHomeworkAnalysisData(Student student, Map<String, Object> data) {
        List<Submission> submissions = submissionRepository.findByStudent(student);
        List<Submission> graded = submissions.stream()
            .filter(s -> s.getScore() != null)
            .collect(Collectors.toList());
        
        if (graded.isEmpty()) {
            data.put("homeworkStatus", "暂无作业数据");
            return;
        }
        
        List<Map<String, Object>> homeworks = new ArrayList<>();
        double totalScore = 0;
        int onTimeCount = 0;
        
        for (Submission sub : graded) {
            Map<String, Object> hw = new HashMap<>();
            hw.put("homeworkName", sub.getHomework().getName());
            hw.put("score", sub.getScore());
            hw.put("totalScore", sub.getHomework().getTotalScore());
            hw.put("classAvg", sub.getHomework().getAvgScore());
            hw.put("isLate", sub.getSubmissionLateMinutes() != null && sub.getSubmissionLateMinutes() > 0);
            homeworks.add(hw);
            totalScore += sub.getScore();
            
            if (sub.getSubmissionLateMinutes() == null || sub.getSubmissionLateMinutes() == 0) {
                onTimeCount++;
            }
        }
        
        data.put("homeworks", homeworks);
        data.put("homeworkCount", graded.size());
        data.put("homeworkAvgScore", totalScore / graded.size());
        data.put("homeworkOnTimeRate", onTimeCount * 100.0 / graded.size());
    }
    
    /**
     * 收集知识点掌握数据
     */
    private void collectKnowledgeAnalysisData(Student student, Map<String, Object> data) {
        List<StudentKnowledgeMastery> masteries = masteryRepository.findByStudent(student);
        if (masteries.isEmpty()) {
            data.put("knowledgeStatus", "暂无知识点数据");
            return;
        }
        
        List<Map<String, Object>> kps = new ArrayList<>();
        double totalMastery = 0;
        int weakCount = 0;
        int strongCount = 0;
        
        for (StudentKnowledgeMastery m : masteries) {
            Map<String, Object> kp = new HashMap<>();
            kp.put("name", m.getKnowledgePoint().getName());
            kp.put("masteryLevel", m.getMasteryLevel());
            kp.put("weaknessLevel", m.getWeaknessLevel());
            kps.add(kp);
            totalMastery += m.getMasteryLevel();
            
            if (m.getMasteryLevel() < 60) weakCount++;
            if (m.getMasteryLevel() >= 80) strongCount++;
        }
        
        data.put("knowledgePoints", kps);
        data.put("knowledgePointCount", masteries.size());
        data.put("avgMasteryRate", totalMastery / masteries.size());
        data.put("weakKnowledgePointCount", weakCount);
        data.put("strongKnowledgePointCount", strongCount);
    }
    
    /**
     * 收集活跃度数据
     */
    private void collectActivityData(Student student, Map<String, Object> data) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ActivityRecord> activities = activityRecordRepository
            .findByStudentAndActivityDateBetween(student, thirtyDaysAgo, LocalDateTime.now());
        
        if (activities.isEmpty()) {
            data.put("activityStatus", "暂无活跃度数据");
            return;
        }
        
        int totalDuration = activities.stream().mapToInt(ActivityRecord::getStudyDuration).sum();
        int loginCount = (int) activities.stream()
            .filter(a -> a.getType() == ActivityStatus.LOGIN)
            .count();
        
        data.put("activityCount", activities.size());
        data.put("studyDuration", totalDuration);
        data.put("loginCount", loginCount);
        data.put("activeDays", activities.stream()
            .map(a -> a.getActivityDate().toLocalDate())
            .distinct()
            .count());
    }
    
    /**
     * 收集班级数据
     */
    private Map<String, Object> collectClassData(Long classId) {
        ClassInfo classInfo = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("班级不存在: " + classId));
        
        Map<String, Object> data = new HashMap<>();
        data.put("classId", classInfo.getId());
        data.put("className", classInfo.getName());
        data.put("grade", classInfo.getGrade());
        
        List<Student> students = studentRepository.findByClassInfo(classInfo);
        data.put("studentCount", students.size());
        
        if (students.isEmpty()) {
            return data;
        }
        
        // 收集所有学生的成绩
        List<Double> allScores = new ArrayList<>();
        Map<String, List<Double>> kpMasteries = new HashMap<>();
        
        for (Student student : students) {
            List<ExamGrade> examGrades = examGradeRepository.findByStudent(student);
            for (ExamGrade eg : examGrades) {
                if (eg.getScore() != null) {
                    allScores.add(eg.getScore().doubleValue());
                }
            }
            
            List<StudentKnowledgeMastery> masteries = masteryRepository.findByStudent(student);
            for (StudentKnowledgeMastery m : masteries) {
                String kpName = m.getKnowledgePoint().getName();
                kpMasteries.computeIfAbsent(kpName, k -> new ArrayList<>()).add(m.getMasteryLevel());
            }
        }
        
        // 统计成绩
        if (!allScores.isEmpty()) {
            data.put("classAvgScore", allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            data.put("classHighestScore", allScores.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            data.put("classLowestScore", allScores.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            long passCount = allScores.stream().filter(s -> s >= 60).count();
            data.put("passRate", passCount * 100.0 / allScores.size());
        }
        
        // 统计薄弱知识点
        List<Map<String, Object>> weakKps = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : kpMasteries.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (avg < 60) {
                Map<String, Object> weak = new HashMap<>();
                weak.put("knowledgePointName", entry.getKey());
                weak.put("avgMastery", avg);
                weakKps.add(weak);
            }
        }
        data.put("weakKnowledgePoints", weakKps);
        
        return data;
    }
    
    /**
     * 收集课程数据
     */
    private Map<String, Object> collectCourseData(Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在: " + courseId));
        
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", course.getId());
        data.put("courseName", course.getName());
        data.put("credit", course.getCredit());
        
        // 考试成绩
        List<ExamGrade> grades = examGradeRepository.findByCourseId(courseId);
        if (!grades.isEmpty()) {
            List<Integer> scores = grades.stream().map(ExamGrade::getScore).collect(Collectors.toList());
            data.put("studentCount", scores.size());
            data.put("avgScore", scores.stream().mapToInt(Integer::intValue).average().orElse(0));
            data.put("highestScore", scores.stream().mapToInt(Integer::intValue).max().orElse(0));
            data.put("lowestScore", scores.stream().mapToInt(Integer::intValue).min().orElse(0));
            long passCount = scores.stream().filter(s -> s >= 60).count();
            data.put("passRate", passCount * 100.0 / scores.size());
            long excellentCount = scores.stream().filter(s -> s >= 80).count();
            data.put("excellentRate", excellentCount * 100.0 / scores.size());
        }
        
        // 作业
        List<Homework> homeworks = homeworkRepository.findByCourse(course);
        if (!homeworks.isEmpty()) {
            data.put("homeworkCount", homeworks.size());
            List<Double> hwAvgScores = new ArrayList<>();
            for (Homework hw : homeworks) {
                if (hw.getAvgScore() != null) {
                    hwAvgScores.add(hw.getAvgScore().doubleValue());
                }
            }
            if (!hwAvgScores.isEmpty()) {
                data.put("homeworkAvgScore", hwAvgScores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            }
        }
        
        return data;
    }
    
    /**
     * 收集单次考试数据
     */
    private Map<String, Object> collectExamData(Long examId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("考试不存在: " + examId));
        
        Map<String, Object> data = new HashMap<>();
        data.put("examId", exam.getId());
        data.put("examName", exam.getName());
        data.put("courseName", exam.getCourse().getName());
        data.put("fullScore", exam.getFullScore());
        data.put("passScore", exam.getPassScore());
        data.put("examDate", exam.getExamDate());
        
        List<ExamGrade> grades = examGradeRepository.findByExam(exam);
        if (!grades.isEmpty()) {
            List<Integer> scores = grades.stream()
                .map(ExamGrade::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            data.put("totalStudents", scores.size());
            data.put("avgScore", scores.stream().mapToInt(Integer::intValue).average().orElse(0));
            data.put("highestScore", scores.stream().mapToInt(Integer::intValue).max().orElse(0));
            data.put("lowestScore", scores.stream().mapToInt(Integer::intValue).min().orElse(0));
            
            long passCount = scores.stream().filter(s -> s >= exam.getPassScore()).count();
            data.put("passRate", passCount * 100.0 / scores.size());
            
            long excellentCount = scores.stream().filter(s -> s >= 80).count();
            data.put("excellentRate", excellentCount * 100.0 / scores.size());
            
            // 分数段分布
            Map<String, Integer> distribution = new LinkedHashMap<>();
            distribution.put("90-100", 0);
            distribution.put("80-89", 0);
            distribution.put("70-79", 0);
            distribution.put("60-69", 0);
            distribution.put("0-59", 0);
            
            for (Integer score : scores) {
                if (score >= 90) distribution.put("90-100", distribution.get("90-100") + 1);
                else if (score >= 80) distribution.put("80-89", distribution.get("80-89") + 1);
                else if (score >= 70) distribution.put("70-79", distribution.get("70-79") + 1);
                else if (score >= 60) distribution.put("60-69", distribution.get("60-69") + 1);
                else distribution.put("0-59", distribution.get("0-59") + 1);
            }
            data.put("scoreDistribution", distribution);
        }
        
        return data;
    }
    
    /**
     * 收集单次作业数据
     */
    private Map<String, Object> collectHomeworkData(Long homeworkId) {
        Homework homework = homeworkRepository.findById(homeworkId)
            .orElseThrow(() -> new RuntimeException("作业不存在: " + homeworkId));
        
        Map<String, Object> data = new HashMap<>();
        data.put("homeworkId", homework.getId());
        data.put("homeworkName", homework.getName());
        data.put("courseName", homework.getCourse().getName());
        data.put("totalScore", homework.getTotalScore());
        data.put("deadline", homework.getDeadline());
        
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homeworkId);
        if (!submissions.isEmpty()) {
            List<Double> scores = submissions.stream()
                .filter(s -> s.getScore() != null)
                .map(Submission::getScore)
                .collect(Collectors.toList());
            
            data.put("totalStudents", submissions.size());
            data.put("submittedCount", scores.size());
            data.put("avgScore", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            data.put("highestScore", scores.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            data.put("lowestScore", scores.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            
            long passCount = scores.stream().filter(s -> s >= 60).count();
            data.put("passRate", passCount * 100.0 / scores.size());
            
            long onTimeCount = submissions.stream()
                .filter(s -> s.getSubmissionLateMinutes() == null || s.getSubmissionLateMinutes() == 0)
                .count();
            data.put("onTimeRate", onTimeCount * 100.0 / submissions.size());
        }
        
        return data;
    }
    
    /**
     * 计算数据哈希（MD5）
     */
    private String calculateDataHash(Map<String, Object> data) {
        try {
            String jsonStr = JSON.toJSONString(data);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(jsonStr.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算哈希失败", e);
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * 保存报告到数据库
     */
    private void saveReport(String targetType, Long targetId, String reportType, 
                            String dataHash, Map<String, Object> sourceData, 
                            AiSuggestionDTO aiResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            AiAnalysisReport report = AiAnalysisReport.builder()
                .targetType(targetType)
                .targetId(targetId)
                .reportType(reportType)
                .dataHash(dataHash)
                .analysisData(mapper.writeValueAsString(sourceData))
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .strengths(mapper.writeValueAsString(aiResponse.getStrengths()))
                .weaknesses(mapper.writeValueAsString(aiResponse.getWeaknesses()))
                .chartsConfig("{}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            aiReportService.save(report);
            log.info("保存AI分析报告成功: targetType={}, targetId={}", targetType, targetId);
        } catch (Exception e) {
            log.error("保存AI分析报告失败", e);
        }
    }
    
    /**
     * 转换为DTO
     */
    private AiSuggestionDTO convertToAiSuggestionDTO(AiAnalysisReport report) {
        AiSuggestionDTO dto = new AiSuggestionDTO();
        dto.setSummary(report.getSummary());
        
        List<String> suggestions = new ArrayList<>();
        if (report.getSuggestions() != null) {
            suggestions = Arrays.asList(report.getSuggestions().split("\n"));
        }
        dto.setSuggestions(suggestions);
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (report.getStrengths() != null && !report.getStrengths().isEmpty()) {
                List<String> strengths = mapper.readValue(report.getStrengths(), List.class);
                dto.setStrengths(strengths);
            } else {
                dto.setStrengths(new ArrayList<>());
            }
            if (report.getWeaknesses() != null && !report.getWeaknesses().isEmpty()) {
                List<String> weaknesses = mapper.readValue(report.getWeaknesses(), List.class);
                dto.setWeaknesses(weaknesses);
            } else {
                dto.setWeaknesses(new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("解析strengths/weaknesses失败", e);
            dto.setStrengths(new ArrayList<>());
            dto.setWeaknesses(new ArrayList<>());
        }
        
        return dto;
    }
    
    /**
     * 创建降级报告（AI失败时使用）
     */
    private AiSuggestionDTO createFallbackReport(String targetType, Long targetId) {
        AiSuggestionDTO fallback = new AiSuggestionDTO();
        
        String targetName = getTargetName(targetType, targetId);
        
        fallback.setSummary(String.format(
            "【%s】当前数据量不足，无法生成详细的AI分析报告。建议补充更多学习数据后重试。",
            targetName));
        
        fallback.setStrengths(Arrays.asList("系统已准备就绪", "等待更多数据积累"));
        fallback.setWeaknesses(Arrays.asList("数据量不足", "无法进行深度分析"));
        fallback.setSuggestions(Arrays.asList(
            "1. 请确保已录入完整的考试成绩数据",
            "2. 请确保已批改作业并录入成绩",
            "3. 数据积累足够后，系统将自动生成详细分析报告",
            "4. 如有疑问，请联系技术支持"
        ));
        
        return fallback;
    }
    
    /**
     * 获取目标名称
     */
    private String getTargetName(String targetType, Long targetId) {
        try {
            switch (targetType) {
                case "STUDENT":
                    return studentRepository.findById(targetId)
                        .map(s -> s.getUser().getName())
                        .orElse("学生");
                case "CLASS":
                    return classRepository.findById(targetId)
                        .map(ClassInfo::getName)
                        .orElse("班级");
                case "COURSE":
                    return courseRepository.findById(targetId)
                        .map(Course::getName)
                        .orElse("课程");
                case "EXAM":
                    return examRepository.findById(targetId)
                        .map(Exam::getName)
                        .orElse("考试");
                case "HOMEWORK":
                    return homeworkRepository.findById(targetId)
                        .map(Homework::getName)
                        .orElse("作业");
                default:
                    return "目标";
            }
        } catch (Exception e) {
            return "目标";
        }
    }
    
    /**
     * 获取数据类型描述（用于AI提示词）
     */
    private String getDataTypeDescription(String targetType, String reportType) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("STUDENT_EXAM_ANALYSIS", "学生考试成绩分析");
        typeMap.put("STUDENT_HOMEWORK_ANALYSIS", "学生作业完成情况分析");
        typeMap.put("STUDENT_KNOWLEDGE_ANALYSIS", "学生知识点掌握情况分析");
        typeMap.put("STUDENT_COMPREHENSIVE", "学生综合学情分析");
        typeMap.put("CLASS_COMPREHENSIVE", "班级整体学情分析");
        typeMap.put("COURSE_COMPREHENSIVE", "课程整体学情分析");
        typeMap.put("EXAM_COMPREHENSIVE", "考试分析");
        typeMap.put("HOMEWORK_COMPREHENSIVE", "作业分析");
        
        String key = targetType + "_" + reportType;
        return typeMap.getOrDefault(key, "数据分析");
    }

    private static class DataValidationResult {
    private boolean valid;
    private String reason;
    
    public static DataValidationResult valid() {
        DataValidationResult result = new DataValidationResult();
        result.valid = true;
        return result;
    }
    
    public static DataValidationResult invalid(String reason) {
        DataValidationResult result = new DataValidationResult();
        result.valid = false;
        result.reason = reason;
        return result;
    }
    
    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
}

private DataValidationResult validateDataAvailability(String targetType, Long targetId, 
                                                       String reportType, Map<String, Object> sourceData) {
    switch (targetType) {
        case "STUDENT":
            return validateStudentData(targetId, reportType, sourceData);
        case "CLASS":
            return validateClassData(targetId, sourceData);
        case "COURSE":
            return validateCourseData(targetId, sourceData);
        case "EXAM":
            return validateExamData(targetId, sourceData);
        case "HOMEWORK":
            return validateHomeworkData(targetId, sourceData);
        default:
            return DataValidationResult.valid();
    }
}

/**
 * 验证学生数据
 */
private DataValidationResult validateStudentData(Long studentId, String reportType, Map<String, Object> sourceData) {
    // 单次考试分析：检查是否有该考试的成绩
    if (reportType != null && reportType.startsWith("EXAM_ANALYSIS_")) {
        String examIdStr = reportType.substring("EXAM_ANALYSIS_".length());
        Long examId = Long.parseLong(examIdStr);
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(examId, studentId);
        if (!grade.isPresent() || grade.get().getScore() == null) {
            return DataValidationResult.invalid("该考试暂无成绩数据");
        }
        return DataValidationResult.valid();
    }
    
    // 单次作业分析：检查是否有该作业的提交记录且已批改
    if (reportType != null && reportType.startsWith("HOMEWORK_ANALYSIS_")) {
        String homeworkIdStr = reportType.substring("HOMEWORK_ANALYSIS_".length());
        Long homeworkId = Long.parseLong(homeworkIdStr);
        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(studentId,homeworkId);
        if (!submission.isPresent() || submission.get().getScore() == null) {
            return DataValidationResult.invalid("该作业未提交或尚未批改");
        }
        return DataValidationResult.valid();
    }
    
    // 考试整体分析：检查是否有任何考试成绩
    if ("EXAM_OVERALL".equals(reportType)) {
        List<ExamGrade> grades = examGradeRepository.findByStudentId(studentId);
        if (grades == null || grades.isEmpty()) {
            return DataValidationResult.invalid("暂无任何考试成绩数据");
        }
        return DataValidationResult.valid();
    }
    
    // 作业整体分析：检查是否有任何已批改的作业
    if ("HOMEWORK_OVERALL".equals(reportType)) {
        List<Submission> submissions = submissionRepository.findGradedByStudentId(studentId);
        if (submissions == null || submissions.isEmpty()) {
            return DataValidationResult.invalid("暂无任何已批改的作业数据");
        }
        return DataValidationResult.valid();
    }
    
    // 知识点分析：检查是否有知识点掌握数据
    if ("KNOWLEDGE_ANALYSIS".equals(reportType) || 
        (reportType != null && reportType.startsWith("KNOWLEDGE_ANALYSIS_COURSE_"))) {
        List<StudentKnowledgeMastery> masteries = masteryRepository.findByStudentId(studentId);
        if (masteries == null || masteries.isEmpty()) {
            return DataValidationResult.invalid("暂无知识点掌握数据，请先完成作业和考试");
        }
        return DataValidationResult.valid();
    }
    
    // 综合分析：检查是否有足够的数据
    if ("COMPREHENSIVE".equals(reportType)) {
        List<ExamGrade> examGrades = examGradeRepository.findByStudentId(studentId);
        List<Submission> submissions = submissionRepository.findGradedByStudentId(studentId);
        List<StudentKnowledgeMastery> masteries = masteryRepository.findByStudentId(studentId);
        
        if ((examGrades == null || examGrades.isEmpty()) && 
            (submissions == null || submissions.isEmpty()) && 
            (masteries == null || masteries.isEmpty())) {
            return DataValidationResult.invalid("暂无学习数据，请先完成作业和考试");
        }
        return DataValidationResult.valid();
    }
    
    return DataValidationResult.valid();
}

/**
 * 验证班级数据
 */
private DataValidationResult validateClassData(Long classId, Map<String, Object> sourceData) {
    // 检查是否有成绩数据
    Object avgScore = sourceData.get("classAvgScore");
    if (avgScore == null || (avgScore instanceof Number && ((Number) avgScore).doubleValue() == 0)) {
        // 进一步检查是否有任何考试数据
        List<Exam> exams = examRepository.findByClassId(classId);
        if (exams == null || exams.isEmpty()) {
            return DataValidationResult.invalid("该班级暂无任何考试数据");
        }
    }
    return DataValidationResult.valid();
}

/**
 * 验证课程数据
 */
private DataValidationResult validateCourseData(Long courseId, Map<String, Object> sourceData) {
    Object avgScore = sourceData.get("avgScore");
    if (avgScore == null || (avgScore instanceof Number && ((Number) avgScore).doubleValue() == 0)) {
        List<ExamGrade> grades = examGradeRepository.findByCourseId(courseId);
        if (grades == null || grades.isEmpty()) {
            return DataValidationResult.invalid("该课程暂无任何成绩数据");
        }
    }
    return DataValidationResult.valid();
}

/**
 * 验证考试数据
 */
private DataValidationResult validateExamData(Long examId, Map<String, Object> sourceData) {
    Integer totalStudents = (Integer) sourceData.get("totalStudents");
    if (totalStudents == null || totalStudents == 0) {
        return DataValidationResult.invalid("该考试暂无成绩数据");
    }
    return DataValidationResult.valid();
}

/**
 * 验证作业数据
 */
private DataValidationResult validateHomeworkData(Long homeworkId, Map<String, Object> sourceData) {
    Integer totalStudents = (Integer) sourceData.get("totalStudents");
    Integer submittedCount = (Integer) sourceData.get("submittedCount");
    if (totalStudents == null || totalStudents == 0 || submittedCount == null || submittedCount == 0) {
        return DataValidationResult.invalid("该作业暂无提交记录或尚未批改");
    }
    return DataValidationResult.valid();
}

/**
 * 创建无数据报告（不调用AI，直接返回提示）
 */
private AiSuggestionDTO createNoDataReport(String targetType, Long targetId, String reportType, String reason) {
    String targetName = getTargetName(targetType, targetId);
    String summary = generateNoDataSummary(targetType, reportType, targetName, reason);
    
    return AiSuggestionDTO.builder()
        .summary(summary)
        .strengths(new ArrayList<>())
        .weaknesses(new ArrayList<>())
        .suggestions(generateNoDataSuggestions(targetType, reportType))
        .build();
}

/**
 * 生成无数据时的摘要信息
 */
private String generateNoDataSummary(String targetType, String reportType, String targetName, String reason) {
    switch (targetType) {
        case "STUDENT":
            if (reportType != null && reportType.startsWith("EXAM_ANALYSIS_")) {
                return String.format("【%s】%s，暂时无法生成详细的考试成绩分析。请等待老师录入成绩后重试。", targetName, reason);
            }
            if (reportType != null && reportType.startsWith("HOMEWORK_ANALYSIS_")) {
                return String.format("【%s】%s，暂时无法生成详细的作业分析。请先完成作业并等待老师批改。", targetName, reason);
            }
            if ("EXAM_OVERALL".equals(reportType)) {
                return String.format("【%s】暂无任何考试成绩数据，暂时无法生成考试分析报告。请先参加考试。", targetName);
            }
            if ("HOMEWORK_OVERALL".equals(reportType)) {
                return String.format("【%s】暂无任何已批改的作业数据，暂时无法生成作业分析报告。请先完成作业。", targetName);
            }
            if (reportType != null && reportType.startsWith("KNOWLEDGE_ANALYSIS")) {
                return String.format("【%s】%s，暂时无法生成知识点分析报告。请先完成作业和考试。", targetName, reason);
            }
            return String.format("【%s】%s，暂时无法生成学习分析报告。请先完成更多学习活动。", targetName, reason);
            
        case "EXAM":
            return String.format("【%s】%s，暂时无法生成考试分析报告。请先录入考试成绩。", targetName, reason);
            
        case "HOMEWORK":
            return String.format("【%s】%s，暂时无法生成作业分析报告。请先录入作业成绩。", targetName, reason);
            
        case "CLASS":
            return String.format("【%s】%s，暂时无法生成班级学情分析报告。请先录入考试或作业数据。", targetName, reason);
            
        case "COURSE":
            return String.format("【%s】%s，暂时无法生成课程学情分析报告。请先录入成绩数据。", targetName, reason);
            
        default:
            return String.format("【%s】数据不足，暂时无法生成分析报告。", targetName);
    }
}

/**
 * 生成无数据时的建议
 */
private List<String> generateNoDataSuggestions(String targetType, String reportType) {
    List<String> suggestions = new ArrayList<>();
    
    if ("STUDENT".equals(targetType)) {
        if (reportType != null && reportType.startsWith("EXAM_ANALYSIS_")) {
            suggestions.add("1. 请耐心等待老师录入考试成绩");
            suggestions.add("2. 成绩录入后将自动生成详细分析");
        } else if (reportType != null && reportType.startsWith("HOMEWORK_ANALYSIS_")) {
            suggestions.add("1. 请按时完成并提交作业");
            suggestions.add("2. 等待老师批改后即可查看分析");
        } else if ("HOMEWORK_OVERALL".equals(reportType)) {
            suggestions.add("1. 请按时完成布置的作业");
            suggestions.add("2. 作业批改后系统将自动生成分析报告");
        } else if ("EXAM_OVERALL".equals(reportType)) {
            suggestions.add("1. 请认真准备并参加考试");
            suggestions.add("2. 考试成绩录入后将自动生成分析报告");
        } else {
            suggestions.add("1. 请按时完成作业并参加考试");
            suggestions.add("2. 数据积累足够后系统将自动生成分析报告");
        }
    } else if ("EXAM".equals(targetType)) {
        suggestions.add("1. 请通过「录入成绩」功能导入考试成绩");
        suggestions.add("2. 成绩导入后将自动生成考试分析报告");
    } else if ("HOMEWORK".equals(targetType)) {
        suggestions.add("1. 请通过「录入成绩」功能导入作业成绩");
        suggestions.add("2. 成绩导入后将自动生成作业分析报告");
    } else if ("CLASS".equals(targetType)) {
        suggestions.add("1. 请确保已录入考试成绩数据");
        suggestions.add("2. 请确保已批改作业并录入成绩");
        suggestions.add("3. 数据积累后系统将自动生成学情分析报告");
    } else {
        suggestions.add("1. 请确保已录入足够的学习数据");
        suggestions.add("2. 数据积累后系统将自动生成分析报告");
    }
    
    return suggestions;
}



}