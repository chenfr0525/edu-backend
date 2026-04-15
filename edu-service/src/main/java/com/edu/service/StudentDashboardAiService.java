// com/edu/service/StudentDashboardAiService.java
package com.edu.service;

import com.alibaba.fastjson.JSONObject;
import com.edu.domain.ActivityRecord;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.ExamGrade;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.Submission;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardAiService {

    private final DeepSeekService deepSeekService;
    private final AiAnalysisReportService aiReportService;
    private final SemesterService semesterService;
    private final StudentService studentService;
    
    // 注入需要的 Repository
    private final ExamGradeRepository examGradeRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ActivityRecordRepository activityRecordRepository;

    /**
     * 获取学生 Dashboard 的 AI 分析报告
     * 优先从数据库读取，如果没有或过期则调用 AI 生成
     */
    public AiAnalysisReport getOrCreateDashboardReport(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 1. 先查询缓存（7天内有效）
        AiAnalysisReport cachedReport = aiReportService.findLatestReport(
            "STUDENT_DASHBOARD", studentId, "COMPREHENSIVE"
        );
        
        // 如果存在且是7天内的报告，直接返回
        if (cachedReport != null && 
            cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的 Dashboard AI 报告，学生ID: {}, 创建时间: {}", 
                studentId, cachedReport.getCreatedAt());
            return cachedReport;
        }
        
        // 2. 没有缓存或已过期，调用 AI 生成新报告
        log.info("生成新的 Dashboard AI 报告，学生ID: {}", studentId);
        
        AiAnalysisReport newReport = generateDashboardReport(student);
        
        // 3. 保存到数据库
        if (newReport != null) {
            aiReportService.save(newReport);
        }
        
        return newReport;
    }
    
    /**
     * 手动刷新 Dashboard AI 报告
     */
    public AiAnalysisReport refreshDashboardReport(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 删除旧的缓存报告
        List<AiAnalysisReport> oldReports = aiReportService.findByTarget("STUDENT_DASHBOARD", studentId);
        for (AiAnalysisReport report : oldReports) {
            if ("COMPREHENSIVE".equals(report.getReportType())) {
                aiReportService.deleteById(report.getId());
                log.info("删除旧的 Dashboard AI 报告，ID: {}", report.getId());
            }
        }
        
        // 强制重新生成
        AiAnalysisReport newReport = generateDashboardReport(student);
        if (newReport != null) {
            aiReportService.save(newReport);
        }
        return newReport;
    }
    
    /**
     * 调用 AI 生成 Dashboard 综合分析报告
     */
    private AiAnalysisReport generateDashboardReport(Student student) {
        try {
            // 1. 收集学生数据
            JSONObject studentData = collectStudentData(student);
            
            // 2. 构建 Prompt 并调用 AI
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(
                studentData.toJSONString(),
                "学生综合学情分析"
            );
            
            if (aiResponse == null || aiResponse.getSummary() == null) {
                log.error("AI 返回数据为空，学生ID: {}", student.getId());
                return createFallbackReport(student);
            }
            
            // 3. 构建分析数据 JSON
            JSONObject analysisData = new JSONObject();
            analysisData.put("studentId", student.getId());
            analysisData.put("studentName", student.getUser().getName());
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            analysisData.put("dataSummary", studentData);
            analysisData.put("aiSummary", aiResponse.getSummary());
            analysisData.put("aiSuggestions", aiResponse.getSuggestions());
            
            // 4. 获取当前学期
            Semester currentSemester = getCurrentSemester();
            
            // 5. 构建并返回报告
            return AiAnalysisReport.builder()
                .targetType("STUDENT_DASHBOARD")
                .targetId(student.getId())
                .semester(currentSemester)
                .reportType("COMPREHENSIVE")
                .analysisData(analysisData.toJSONString())
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .createdAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("生成 Dashboard AI 报告失败，学生ID: {}", student.getId(), e);
            return createFallbackReport(student);
        }
    }
    
    /**
     * 收集学生所有相关数据，用于 AI 分析
     */
    private JSONObject collectStudentData(Student student) {
        Long studentId = student.getId();
        JSONObject data = new JSONObject();
        data.put("studentName", student.getUser().getName());
        data.put("className", student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班");
        data.put("grade", student.getGrade());
        
        // 1. 考试成绩数据
        List<ExamGrade> examGrades = examGradeRepository.findByStudent(student);
        if (examGrades != null && !examGrades.isEmpty()) {
            List<JSONObject> examList = new ArrayList<>();
            double examTotal = 0;
            for (ExamGrade eg : examGrades) {
                JSONObject exam = new JSONObject();
                exam.put("name", eg.getExam().getName());
                exam.put("score", eg.getScore());
                exam.put("classRank", eg.getClassRank());
                exam.put("scoreTrend", eg.getScoreTrend());
                examList.add(exam);
                examTotal += eg.getScore();
            }
            data.put("exams", examList);
            data.put("examAvgScore", examTotal / examGrades.size());
            data.put("totalExams", examGrades.size());
        } else {
            data.put("totalExams", 0);
        }
        
        // 2. 作业数据
        List<Submission> submissions = submissionRepository.findGradedByStudentId(studentId);
        if (submissions != null && !submissions.isEmpty()) {
            List<JSONObject> homeworkList = new ArrayList<>();
            double homeworkTotal = 0;
            long lateCount = 0;
            for (Submission sub : submissions) {
                JSONObject hw = new JSONObject();
                hw.put("name", sub.getHomework().getName());
                hw.put("score", sub.getScore());
                hw.put("classAvg", sub.getHomework().getAvgScore());
                homeworkList.add(hw);
                homeworkTotal += sub.getScore();
                if (sub.getSubmissionLateMinutes() != null && sub.getSubmissionLateMinutes() > 0) {
                    lateCount++;
                }
            }
            data.put("homeworks", homeworkList);
            data.put("homeworkAvgScore", homeworkTotal / submissions.size());
            data.put("totalHomeworks", submissions.size());
            data.put("homeworkLateCount", lateCount);
            data.put("homeworkOnTimeRate", (submissions.size() - lateCount) * 100.0 / submissions.size());
        } else {
            data.put("totalHomeworks", 0);
        }
        
        // 3. 知识点掌握数据
        List<StudentKnowledgeMastery> masteries = masteryRepository.findAllByStudentId(studentId);
        if (masteries != null && !masteries.isEmpty()) {
            List<JSONObject> kpList = new ArrayList<>();
            double masteryTotal = 0;
            int weakCount = 0;
            int strongCount = 0;
            for (StudentKnowledgeMastery m : masteries) {
                JSONObject kp = new JSONObject();
                kp.put("name", m.getKnowledgePoint().getName());
                kp.put("masteryLevel", m.getMasteryLevel());
                kp.put("weaknessLevel", m.getWeaknessLevel());
                kpList.add(kp);
                masteryTotal += m.getMasteryLevel();
                if (m.getMasteryLevel() < 60) weakCount++;
                if (m.getMasteryLevel() >= 80) strongCount++;
            }
            data.put("knowledgePoints", kpList);
            data.put("avgMasteryRate", masteryTotal / masteries.size());
            data.put("totalKnowledgePoints", masteries.size());
            data.put("weakKnowledgePointCount", weakCount);
            data.put("strongKnowledgePointCount", strongCount);
        } else {
            data.put("totalKnowledgePoints", 0);
        }
        
        // 4. 活跃度数据
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ActivityRecord> recentActivities = activityRecordRepository
            .findByStudentIdAndActivityDateAfter(studentId, thirtyDaysAgo.toLocalDate());
        if (recentActivities != null && !recentActivities.isEmpty()) {
            double totalDuration = recentActivities.stream()
                .mapToInt(ActivityRecord::getStudyDuration)
                .sum();
            data.put("recentActivityCount", recentActivities.size());
            data.put("recentStudyDuration", totalDuration);
            data.put("avgDailyStudyMinutes", totalDuration / 30.0);
        } else {
            data.put("recentActivityCount", 0);
        }
        
        return data;
    }
    
    /**
     * 创建降级报告（AI 调用失败时使用）
     */
    private AiAnalysisReport createFallbackReport(Student student) {
        Semester currentSemester = getCurrentSemester();
        
        String fallbackSummary = "基于当前学习数据，整体表现良好。建议继续保持学习节奏，针对薄弱知识点加强练习。";
        String fallbackSuggestions = "1. 按时完成作业，避免迟交\n2. 针对薄弱知识点进行专项练习\n3. 积极参与课堂互动，提高活跃度\n4. 定期复习错题，巩固记忆";
        
        JSONObject analysisData = new JSONObject();
        analysisData.put("studentId", student.getId());
        analysisData.put("isFallback", true);
        analysisData.put("generatedAt", LocalDateTime.now().toString());
        
        return AiAnalysisReport.builder()
            .targetType("STUDENT_DASHBOARD")
            .targetId(student.getId())
            .semester(currentSemester)
            .reportType("COMPREHENSIVE")
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