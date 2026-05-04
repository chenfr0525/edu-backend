package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.*;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/student")
@RequiredArgsConstructor
public class StudentDashboardController {
    private final StudentService studentService;
    private final ExamGradeService examGradeService;
    private final SubmissionService submissionService;
    private final StudentKnowledgeMasteryService masteryService;
    private final ActivityRecordService activityRecordService;
    private final StudentDashboardAiService studentDashboardAiService;

    /**
     * 学生个人驾驶舱 - 完整数据
     */
    @GetMapping("/{studentId}")
    public Result<Map<String, Object>> getDashboard(@PathVariable Long studentId) {
        Map<String, Object> dashboard = new HashMap<>();
        
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        // 1. 学生基本信息
        Map<String, Object> studentInfoMap = new HashMap<>();
        studentInfoMap.put("id", student.getId());
        studentInfoMap.put("name", student.getUser().getName());
        studentInfoMap.put("studentNo", student.getStudentNo());
        studentInfoMap.put("className", student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班");
        studentInfoMap.put("grade", student.getGrade());
        dashboard.put("studentInfo", studentInfoMap);
        
        // 2. 成绩趋势（历次考试分数）
        List<ExamGrade> scoreTrend = examGradeService.getStudentScoreTrend(student);
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (ExamGrade eg : scoreTrend) {
            Map<String, Object> point = new HashMap<>();
            point.put("examId", eg.getExam().getId());
            point.put("examName", eg.getExam().getName());
            point.put("score", eg.getScore());
            point.put("classRank", eg.getClassRank() != null ? eg.getClassRank() : 0);
            point.put("examDate", eg.getExam().getExamDate());
            trendData.add(point);
        }
        dashboard.put("scoreTrend", trendData);
        
        // 3. 平均分和最新排名
        Double avgScore = examGradeService.getStudentAverageScore(studentId);
        dashboard.put("avgScore", avgScore != null ? avgScore : 0);
        
        Integer latestRank = examGradeService.getLatestRank(studentId);
        dashboard.put("latestRank", latestRank != null ? latestRank : 0);
        
        // 4. 成绩趋势（进步/退步/稳定）
        String trend = examGradeService.getLatestTrend(studentId);
        dashboard.put("trend", trend != null ? trend : "STABLE");
        
        // 5. 作业完成情况
        List<Submission> submissions = submissionService.findByStudent(student);
        long totalHomework = submissions.size();
        long completedCount = submissions.stream()
            .filter(s -> "GRADED".equals(s.getStatus()))
            .count();
        dashboard.put("homeworkCompletionRate", totalHomework > 0 ? (completedCount * 100.0 / totalHomework) : 0);
        dashboard.put("totalHomework", totalHomework);
        dashboard.put("completedHomework", completedCount);
        
        // 6. 作业平均分
        Double homeworkAvgScore = submissionService.getStudentAverageScore(studentId);
        dashboard.put("homeworkAvgScore", homeworkAvgScore != null ? homeworkAvgScore : 0);
        
        // 7. 知识点掌握情况（雷达图数据）- 修复：从 student_knowledge_mastery 获取
        List<StudentKnowledgeMastery> masteries = masteryService.findByStudent(student);
        Map<String, BigDecimal> radarData = new LinkedHashMap<>();
        
        if (masteries != null && !masteries.isEmpty()) {
            for (StudentKnowledgeMastery mastery : masteries) {
                if (mastery.getKnowledgePoint() != null) {
                    radarData.put(mastery.getKnowledgePoint().getName(), 
                        BigDecimal.valueOf(mastery.getMasteryLevel()));
                }
            }
        }
        dashboard.put("knowledgeRadarData", radarData);
        
        // 8. 薄弱知识点（掌握度低于60%）
        List<StudentKnowledgeMastery> weakPoints = masteryService.findWeakPoints(student, 60.0);
        dashboard.put("weakPoints", weakPoints.stream().map(wp -> {
            Map<String, Object> map = new HashMap<>();
            map.put("knowledgePointId", wp.getKnowledgePoint().getId());
            map.put("knowledgePointName", wp.getKnowledgePoint().getName());
            map.put("masteryLevel", wp.getMasteryLevel());
            return map;
        }).collect(Collectors.toList()));
        
        // 9. 优势知识点（掌握度高于80%）
        List<StudentKnowledgeMastery> strongPoints = masteryService.findStrongPoints(student, 80.0);
        dashboard.put("strongPoints", strongPoints.stream().map(sp -> {
            Map<String, Object> map = new HashMap<>();
            map.put("knowledgePointId", sp.getKnowledgePoint().getId());
            map.put("knowledgePointName", sp.getKnowledgePoint().getName());
            map.put("masteryLevel", sp.getMasteryLevel());
            return map;
        }).collect(Collectors.toList()));
        
        // 10. 活跃度统计（近30天）
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ActivityRecord> recentActivities = activityRecordService
            .findByStudentAndDateRange(student, thirtyDaysAgo, LocalDate.now());
        dashboard.put("recentActivityCount", recentActivities.size());
        
        Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
        dashboard.put("activityScore", activityScore != null ? activityScore : 0);
        
        // 11. 出勤率（近30天有活动记录的天数比例）
        long activeDays = recentActivities.stream()
            .map(ActivityRecord::getActivityDate)
            .map(java.time.LocalDateTime::toLocalDate)
            .distinct()
            .count();
        double attendanceRate = thirtyDaysAgo != null ? Math.min(activeDays * 100.0 / 30, 100) : 0;
        dashboard.put("attendanceRate", attendanceRate);
        
        // 12. 出勤热力图数据（近365天）
        List<Map<String, Object>> heatmapData = buildHeatmapDataPastYear(student);
        dashboard.put("heatmapData", heatmapData);
        
        // 13. 活跃度预警
        if (activityScore != null && activityScore < 50) {
            dashboard.put("activityWarning", "⚠️ 活跃度偏低，建议增加学习互动");
            dashboard.put("activityWarningLevel", "WARNING");
        } else if (activityScore != null && activityScore < 30) {
            dashboard.put("activityWarning", "🔴 活跃度过低，请及时参与学习活动");
            dashboard.put("activityWarningLevel", "CRITICAL");
        } else {
            dashboard.put("activityWarning", null);
            dashboard.put("activityWarningLevel", "GOOD");
        }        
        return Result.success(dashboard);
    }
    
    /**
     * 构建近365天的出勤热力图数据
     * 出勤率 = 当天有活动记录则该天出勤100%，否则0%
     */
    private List<Map<String, Object>> buildHeatmapDataPastYear(Student student) {
        List<Map<String, Object>> heatmapData = new ArrayList<>();
        
        // 计算过去365天的日期范围
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(364); // 包含今天，共365天
        
        // 获取该学生过去一年的所有活动记录
        List<ActivityRecord> yearActivities = activityRecordService
            .findByStudentAndDateRange(student, startDate, endDate);
        
        // 按日期统计是否有活动记录
        Set<LocalDate> activeDates = yearActivities.stream()
            .map(record -> record.getActivityDate().toLocalDate())
            .collect(Collectors.toSet());
        
        // 构建热力图数据
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            boolean hasActivity = activeDates.contains(current);
            // 有活动记录则出勤率100%，否则0%
            int attendancePercent = hasActivity ? 100 : 0;
            
            Map<String, Object> point = new HashMap<>();
            point.put("value", new Object[]{current.format(formatter), attendancePercent});
            heatmapData.add(point);
            
            current = current.plusDays(1);
        }
        
        return heatmapData;
    }
    
    /**
     * 获取知识点雷达图专用数据
     */
    @GetMapping("/{studentId}/radar")
    public Result<Map<String, Object>> getRadarData(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        List<StudentKnowledgeMastery> masteries = masteryService.findByStudent(student);
        
        List<String> indicators = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        
        for (StudentKnowledgeMastery mastery : masteries) {
            if (mastery.getKnowledgePoint() != null) {
                indicators.add(mastery.getKnowledgePoint().getName());
                values.add(BigDecimal.valueOf(mastery.getMasteryLevel()));
            }
        }
        
        double avgMastery = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        
        Map<String, Object> radarData = new HashMap<>();
        radarData.put("indicators", indicators);
        radarData.put("values", values);
        radarData.put("avgMastery", avgMastery);
        
        // 如果没有数据，添加提示信息
        if (indicators.isEmpty()) {
            radarData.put("empty", true);
            radarData.put("message", "暂无知识点数据，请先完成作业和考试");
        }
        
        return Result.success(radarData);
    }
    
    /**
     * 获取学习建议
     */
    @GetMapping("/{studentId}/suggestions")
    public Result<Map<String, Object>> getLearningSuggestions(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        // 1. 基于薄弱知识点生成建议
        List<StudentKnowledgeMastery> weakPoints = masteryService.findWeakPoints(student, 60.0);
        if (!weakPoints.isEmpty()) {
            Map<String, Object> weakSuggestion = new HashMap<>();
            weakSuggestion.put("type", "WEAK_POINTS");
            weakSuggestion.put("title", "📚 薄弱知识点提醒");
            weakSuggestion.put("content", "以下知识点掌握度较低，建议重点复习：");
            weakSuggestion.put("details", weakPoints.stream().map(wp -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", wp.getKnowledgePoint().getName());
                map.put("mastery", wp.getMasteryLevel());
                map.put("suggestion", "建议加强练习，掌握度需提升至60%以上");
                return map;
            }).collect(Collectors.toList()));
            suggestions.add(weakSuggestion);
        }
        
        // 2. 基于活跃度生成建议
        Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
        if (activityScore != null && activityScore < 50) {
            Map<String, Object> activitySuggestion = new HashMap<>();
            activitySuggestion.put("type", "LOW_ACTIVITY");
            activitySuggestion.put("title", "🏃 活跃度提醒");
            activitySuggestion.put("content", "近期学习活跃度偏低，建议：");
            activitySuggestion.put("details", Arrays.asList("每天登录系统查看学习任务", "按时提交作业，积极参与讨论", "观看教学视频，增加学习时长"));
            suggestions.add(activitySuggestion);
        }
        
        // 3. 基于作业完成情况生成建议
        List<Submission> submissions = submissionService.findByStudent(student);
        long lateCount = submissionService.countLateByStudent(student);
        if (lateCount > 0) {
            Map<String, Object> lateSuggestion = new HashMap<>();
            lateSuggestion.put("type", "LATE_SUBMISSION");
            lateSuggestion.put("title", "⏰ 作业提交提醒");
            lateSuggestion.put("content", "您有 " + lateCount + " 次作业迟交，建议提前规划时间");
            suggestions.add(lateSuggestion);
        }
        
        result.put("suggestions", suggestions);
        result.put("totalWeakPoints", weakPoints.size());
        result.put("activityLevel", activityScore != null ? 
            (activityScore >= 70 ? "HIGH" : activityScore >= 40 ? "MEDIUM" : "LOW") : "UNKNOWN");
        
        return Result.success(result);
    }
    
    /**
     * 手动刷新 Dashboard AI 分析报告
     */
    @PostMapping("/refresh/{studentId}")
    public Result<AiAnalysisReport> refreshDashboardAi(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        AiAnalysisReport report = studentDashboardAiService.refreshDashboardReport(studentId);
        return Result.success(report);
    }
}