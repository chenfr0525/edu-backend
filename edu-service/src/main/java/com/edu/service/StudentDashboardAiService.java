// com/edu/service/StudentDashboardAiService.java
package com.edu.service;

import com.alibaba.fastjson.JSONObject;
import com.edu.domain.ActivityRecord;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.ExamGrade;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.Submission;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardAiService {

    private final DeepSeekService deepSeekService;
    private final AiAnalysisReportService aiReportService;
    private final StudentService studentService;
    private final ExamGradeRepository examGradeRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ActivityRecordRepository activityRecordRepository;

    /**
     * 获取学生 Dashboard 的 AI 分析报告
     */
    public AiAnalysisReport getOrCreateDashboardReport(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 1. 先查询缓存（7天内有效）
        AiAnalysisReport cachedReport = aiReportService.findLatestReport(
            "STUDENT_DASHBOARD", studentId, "COMPREHENSIVE"
        );
        
        if (cachedReport != null && 
            cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的 Dashboard AI 报告，学生ID: {}, 创建时间: {}", 
                studentId, cachedReport.getCreatedAt());
            return cachedReport;
        }
        
        // 2. 生成新报告
        log.info("生成新的 Dashboard AI 报告，学生ID: {}", studentId);
        
        AiAnalysisReport newReport = generateDashboardReport(student);
        
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
            JSONObject studentData = collectStudentData(student);
            
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(
                studentData.toJSONString(),
                "学生综合学情分析"
            );
            
            if (aiResponse == null || aiResponse.getSummary() == null) {
                log.error("AI 返回数据为空，学生ID: {}", student.getId());
                return createFallbackReport(student);
            }
            
            JSONObject analysisData = new JSONObject();
            analysisData.put("studentId", student.getId());
            analysisData.put("studentName", student.getUser().getName());
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            analysisData.put("dataSummary", studentData);
            analysisData.put("aiSummary", aiResponse.getSummary());
            analysisData.put("aiSuggestions", aiResponse.getSuggestions());
            
            // semester 设为 null
            return AiAnalysisReport.builder()
                .targetType("STUDENT_DASHBOARD")
                .targetId(student.getId())
                .semester(null)
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
     * 收集学生所有相关数据
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
                examList.add(exam);
                examTotal += eg.getScore();
            }
            data.put("exams", examList);
            data.put("examAvgScore", examTotal / examGrades.size());
            data.put("totalExams", examGrades.size());
        } else {
            data.put("totalExams", 0);
        }
        
        // 2. 作业数据（移除 submissionLateMinutes 依赖）
        List<Submission> submissions = submissionRepository.findGradedByStudentId(studentId);
        if (submissions != null && !submissions.isEmpty()) {
            List<JSONObject> homeworkList = new ArrayList<>();
            double homeworkTotal = 0;
            for (Submission sub : submissions) {
                JSONObject hw = new JSONObject();
                hw.put("name", sub.getHomework().getName());
                hw.put("score", sub.getScore());
                hw.put("classAvg", sub.getHomework().getAvgScore());
                homeworkList.add(hw);
                if (sub.getScore() != null) {
                    homeworkTotal += sub.getScore();
                }
            }
            data.put("homeworks", homeworkList);
            data.put("homeworkAvgScore", homeworkTotal / submissions.size());
            data.put("totalHomeworks", submissions.size());
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
            .findByStudentIdAndActivityDateAfter(studentId, thirtyDaysAgo);
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
    
    private AiAnalysisReport createFallbackReport(Student student) {
        String fallbackSummary = "基于当前学习数据，整体表现良好。建议继续保持学习节奏，针对薄弱知识点加强练习。";
        String fallbackSuggestions = "1. 按时完成作业\n2. 针对薄弱知识点进行专项练习\n3. 积极参与课堂互动，提高活跃度\n4. 定期复习错题，巩固记忆";
        
        JSONObject analysisData = new JSONObject();
        analysisData.put("studentId", student.getId());
        analysisData.put("isFallback", true);
        analysisData.put("generatedAt", LocalDateTime.now().toString());
        
        return AiAnalysisReport.builder()
            .targetType("STUDENT_DASHBOARD")
            .targetId(student.getId())
            .semester(null)
            .reportType("COMPREHENSIVE")
            .analysisData(analysisData.toJSONString())
            .summary(fallbackSummary)
            .suggestions(fallbackSuggestions)
            .createdAt(LocalDateTime.now())
            .build();
    }
}