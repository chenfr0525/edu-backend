// com/edu/service/TeacherDashboardAiService.java
package com.edu.service;

import com.alibaba.fastjson.JSONObject;
import com.edu.domain.*;
import com.edu.domain.dto.AiAnalysisReportDTO;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherDashboardAiService {

    private final DeepSeekService deepSeekService;
    private final AiAnalysisReportService aiReportService;
    private final SemesterService semesterService;
    
    // 注入需要的 Repository
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final ExamGradeRepository examGradeRepository;
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;

    /**
     * 获取或创建 AI 分析报告
     * 优先从数据库读取，如果没有或过期则调用 AI 生成
     */
    public AiAnalysisReport getOrCreateReport(String targetType, Long targetId, String reportType) {
        // 1. 先查询缓存（7天内有效）
        AiAnalysisReport cachedReport = aiReportService.findLatestReport(targetType, targetId, reportType);
        
        if (cachedReport != null && 
            cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的 {} AI 报告，目标ID: {}, 创建时间: {}", 
                targetType, targetId, cachedReport.getCreatedAt());
            return cachedReport;
        }
        
        // 2. 没有缓存或已过期，调用 AI 生成新报告
        log.info("生成新的 {} AI 报告，目标ID: {}", targetType, targetId);
        
        AiAnalysisReport newReport = generateReport(targetType, targetId, reportType);
        
        // 3. 保存到数据库
        if (newReport != null) {
            aiReportService.save(newReport);
        }
        
        return newReport;
    }
    
    /**
     * 手动刷新 AI 分析报告
     */
    public AiAnalysisReport refreshReport(String targetType, Long targetId, String reportType) {
        // 删除旧的缓存报告
        List<AiAnalysisReport> oldReports = aiReportService.findByTarget(targetType, targetId);
        for (AiAnalysisReport report : oldReports) {
            if (reportType.equals(report.getReportType())) {
                aiReportService.deleteById(report.getId());
                log.info("删除旧的 {} AI 报告，ID: {}", targetType, report.getId());
            }
        }
        
        // 强制重新生成
        return generateReport(targetType, targetId, reportType);
    }
    
    /**
     * 调用 AI 生成报告
     */
    private AiAnalysisReport generateReport(String targetType, Long targetId, String reportType) {
        try {
            // 1. 收集数据
            JSONObject data = collectDashboardData(targetType, targetId);
            
            // 2. 调用 AI
            String dataType = targetType.equals("CLASS") ? "班级学情分析" : "课程学情分析";
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(data.toJSONString(), dataType);
            
            if (aiResponse == null || aiResponse.getSummary() == null) {
                return createFallbackReport(targetType, targetId, reportType);
            }
            
            // 3. 获取目标名称
            String targetName = "";
            if ("CLASS".equals(targetType)) {
                ClassInfo classInfo = classRepository.findById(targetId).orElse(null);
                targetName = classInfo != null ? classInfo.getName() : "未知班级";
            } else {
                Course course = courseRepository.findById(targetId).orElse(null);
                targetName = course != null ? course.getName() : "未知课程";
            }
            
            // 4. 构建分析数据 JSON
            JSONObject analysisData = new JSONObject();
            analysisData.put("targetId", targetId);
            analysisData.put("targetName", targetName);
            analysisData.put("targetType", targetType);
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            analysisData.put("dataSummary", data);
            analysisData.put("aiSummary", aiResponse.getSummary());
            analysisData.put("aiSuggestions", aiResponse.getSuggestions());
            
            Semester currentSemester = getCurrentSemester();
            
            return AiAnalysisReport.builder()
                .targetType(targetType)
                .targetId(targetId)
                .semester(currentSemester)
                .reportType(reportType)
                .analysisData(analysisData.toJSONString())
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .createdAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("生成 AI 报告失败", e);
            return createFallbackReport(targetType, targetId, reportType);
        }
    }
    
    /**
     * 收集 Dashboard 数据
     */
    private JSONObject collectDashboardData(String targetType, Long targetId) {
        JSONObject data = new JSONObject();

        // 添加参数校验
    if (targetId == null) {
        log.warn("targetId 为空，无法收集仪表盘数据");
        return data;
    }
        
        if ("CLASS".equals(targetType)) {
            ClassInfo classInfo = classRepository.findById(targetId).orElse(null);
            if (classInfo == null) return data;
            
            data.put("className", classInfo.getName());
            data.put("grade", classInfo.getGrade());
            
            List<Student> students = studentRepository.findByClassInfo(classInfo);
            data.put("studentCount", students.size());
            
            // 成绩统计
            List<ExamGrade> allGrades = new ArrayList<>();
            for (Student student : students) {
                allGrades.addAll(examGradeRepository.findByStudent(student));
            }
            
            if (!allGrades.isEmpty()) {
                double avgScore = allGrades.stream()
                    .mapToInt(ExamGrade::getScore)
                    .average()
                    .orElse(0);
                long passCount = allGrades.stream()
                    .filter(g -> g.getScore() >= 60)
                    .count();
                data.put("avgScore", Math.round(avgScore * 100) / 100.0);
                data.put("passRate", Math.round((passCount * 100.0 / allGrades.size()) * 100) / 100.0);
                data.put("totalExams", allGrades.size());
            }
            
            // 知识点掌握统计
            List<StudentKnowledgeMastery> masteries = new ArrayList<>();
            for (Student student : students) {
                masteries.addAll(masteryRepository.findByStudent(student));
            }
            
            if (!masteries.isEmpty()) {
                double avgMastery = masteries.stream()
                    .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                    .average()
                    .orElse(0);
                long weakCount = masteries.stream()
                    .filter(m -> m.getMasteryLevel() < 60)
                    .count();
                data.put("avgMastery", Math.round(avgMastery * 100) / 100.0);
                data.put("weakPointCount", weakCount);
            }
            
        } else {
            Course course = courseRepository.findById(targetId).orElse(null);
            if (course == null) return data;
            
            data.put("courseName", course.getName());
            data.put("credit", course.getCredit());
            
            // 课程成绩统计
            List<ExamGrade> grades = examGradeRepository.findByCourseId(targetId);
            if (!grades.isEmpty()) {
                double avgScore = grades.stream()
                    .mapToInt(ExamGrade::getScore)
                    .average()
                    .orElse(0);
                data.put("avgScore", Math.round(avgScore * 100) / 100.0);
                data.put("studentCount", grades.size());
            }
            
            // 作业统计
            List<Homework> homeworks = homeworkRepository.findByCourse(course);
            if (!homeworks.isEmpty()) {
                data.put("totalHomeworks", homeworks.size());
                
                List<Submission> allSubmissions = new ArrayList<>();
                for (Homework hw : homeworks) {
                    allSubmissions.addAll(submissionRepository.findByHomework(hw));
                }
                if (!allSubmissions.isEmpty()) {
                    double homeworkAvg = allSubmissions.stream()
                        .filter(s -> s.getScore() != null)
                       .mapToDouble(Submission::getScore)  
                        .average()
                        .orElse(0);
                    data.put("homeworkAvgScore", Math.round(homeworkAvg * 100) / 100.0);
                }
            }
        }
        
        return data;
    }
    
    /**
     * 创建降级报告
     */
    private AiAnalysisReport createFallbackReport(String targetType, Long targetId, String reportType) {
      // 添加参数校验
    if (targetId == null) {
        log.error("targetId 为空，无法创建降级报告");
        throw new IllegalArgumentException("targetId 不能为空");
    }

        Semester currentSemester = getCurrentSemester();
        
        String targetName = "";
        if ("CLASS".equals(targetType)) {
            ClassInfo classInfo = classRepository.findById(targetId).orElse(null);
            targetName = classInfo != null ? classInfo.getName() : "未知班级";
        } else {
            Course course = courseRepository.findById(targetId).orElse(null);
            targetName = course != null ? course.getName() : "未知课程";
        }
        
        String fallbackSummary = String.format(
            "%s 整体学情良好。建议关注薄弱知识点，加强针对性教学。",
            targetName
        );
        String fallbackSuggestions = "1. 定期分析学生成绩，及时发现薄弱环节\n2. 针对高频错题进行专项讲解\n3. 关注低活跃度学生，提高学习参与度\n4. 组织学习小组，促进互帮互助";
        
        JSONObject analysisData = new JSONObject();
        analysisData.put("targetId", targetId);
        analysisData.put("isFallback", true);
        analysisData.put("generatedAt", LocalDateTime.now().toString());
        
        return AiAnalysisReport.builder()
            .targetType(targetType)
            .targetId(targetId)
            .semester(currentSemester)
            .reportType(reportType)
            .analysisData(analysisData.toJSONString())
            .summary(fallbackSummary)
            .suggestions(fallbackSuggestions)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private Semester getCurrentSemester() {
        List<Semester> semesters = semesterService.findAll();
        return semesters.stream()
                .filter(Semester::getIsCurrent)
                .findFirst()
                .orElse(null);
    }
}