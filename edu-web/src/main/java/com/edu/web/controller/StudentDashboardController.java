package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.ActivityRecordService;
import com.edu.service.AiAnalysisReportService;
import com.edu.service.EnrollmentService;
import com.edu.service.ExamGradeService;
import com.edu.service.ExamService;
import com.edu.service.KnowledgePointScoreDetailService;
import com.edu.service.ScorePredictionService;
import com.edu.service.StudentDashboardAiService;
import com.edu.service.StudentKnowledgeMasteryService;
import com.edu.service.StudentService;
import com.edu.service.SubmissionService;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
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
    private final KnowledgePointScoreDetailService kpScoreDetailService;
    private final AiAnalysisReportService aiReportService;
    private final ScorePredictionService predictionService;
    private final ExamService examService;
    private final EnrollmentService enrollmentService;
    private final StudentDashboardAiService studentDashboardAiService;
    
    /**
     * 学生个人驾驶舱 - 完整数据
     * GET /api/dashboard/student/{studentId}
     */
    @GetMapping("/{studentId}")
    public Result<Map<String, Object>> getDashboard(@PathVariable Long studentId) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // 1. 获取学生基本信息
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        Map<String, Object> studentInfoMap = new HashMap<>();
         studentInfoMap.put( "id", student.getId());
         studentInfoMap.put(  "name", student.getUser().getName());
         studentInfoMap.put( "studentNo", student.getStudentNo());
         studentInfoMap.put(  "className", student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班");
         studentInfoMap.put(  "grade", student.getGrade());
         studentInfoMap.put(  "avatar", student.getUser().getAvatar());
        dashboard.put("studentInfo",studentInfoMap);
        
        // 2. 成绩趋势（历次考试分数和排名）
        List<ExamGrade> scoreTrend = examGradeService.getStudentScoreTrend(student);
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (ExamGrade eg : scoreTrend) {
            Map<String, Object> point = new HashMap<>();
            point.put("examId", eg.getExam().getId());
            point.put("examName", eg.getExam().getName());
            point.put("score", eg.getScore());
            point.put("classRank", eg.getClassRank());
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
        dashboard.put("homeworkCompletionRate", totalHomework > 0 ? 
            (completedCount * 100.0 / totalHomework) : 0);
        dashboard.put("totalHomework", totalHomework);
        dashboard.put("completedHomework", completedCount);
        
        // 6. 作业平均分
        Double homeworkAvgScore = submissionService.getStudentAverageScore(studentId);
        dashboard.put("homeworkAvgScore", homeworkAvgScore != null ? homeworkAvgScore : 0);
        
        // 7. 知识点掌握情况（雷达图数据）
        List<KnowledgePointScoreDetail> kpDetails = kpScoreDetailService.findByStudent(student);
        Map<String, BigDecimal> radarData = new LinkedHashMap<>();
        for (KnowledgePointScoreDetail detail : kpDetails) {
            String kpName = detail.getKnowledgePoint().getName();
            // 只保留最新的（如果已存在，比较时间）
            if (!radarData.containsKey(kpName)) {
                radarData.put(kpName, detail.getScoreRate());
            }
        }
        dashboard.put("knowledgeRadarData", radarData);
        
        // 8. 薄弱知识点（掌握度低于60%）
        List<StudentKnowledgeMastery> weakPoints = masteryService.findWeakPoints(student, 60.0);
        dashboard.put("weakPoints", weakPoints.stream().map(wp -> {
             Map<String, Object> map = new HashMap<>();
             map.put("knowledgePointId", wp.getKnowledgePoint().getId());
            map.put( "knowledgePointName", wp.getKnowledgePoint().getName());
            map.put( "masteryLevel", wp.getMasteryLevel());
            return map; 
        }).collect(Collectors.toList()));
        
        // 9. 优势知识点（掌握度高于80%）
        List<StudentKnowledgeMastery> strongPoints = masteryService.findStrongPoints(student, 80.0);
        dashboard.put("strongPoints", strongPoints.stream().map(sp -> {
            Map<String, Object> map = new HashMap<>();
            map.put( "knowledgePointId", sp.getKnowledgePoint().getId());
            map.put( "knowledgePointName", sp.getKnowledgePoint().getName());
            map.put( "masteryLevel", sp.getMasteryLevel());
        return map; 
        }).collect(Collectors.toList()));
        
        // 10. 活跃度统计（近30天）
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<ActivityRecord> recentActivities = activityRecordService
            .findByStudentAndDateRange(student, thirtyDaysAgo, LocalDate.now());
        dashboard.put("recentActivityCount", recentActivities.size());
        
        Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
        dashboard.put("activityScore", activityScore != null ? activityScore : 0);
        
        // 11. 出勤率（有活动记录的天数比例）
        long activeDays = recentActivities.stream()
            .map(ActivityRecord::getActivityDate)
            .distinct()
            .count();
        double attendanceRate = (activeDays * 100.0 / 30);
        dashboard.put("attendanceRate", Math.min(attendanceRate, 100));
        
        // 12. 活跃度预警
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
        
        AiAnalysisReport aiReport = studentDashboardAiService.getOrCreateDashboardReport(studentId);
if (aiReport != null) {
    dashboard.put("aiSuggestions", aiReport.getSuggestions());
    dashboard.put("aiSummary", aiReport.getSummary());
    dashboard.put("aiGeneratedAt", aiReport.getCreatedAt());
} else {
    dashboard.put("aiSuggestions", "暂无AI分析建议，请稍后重试");
    dashboard.put("aiSummary", "数据不足，无法生成分析报告");
    dashboard.put("aiGeneratedAt", null);
}
        
        // 14. 各科成绩预测
        List<Enrollment> enrollments = enrollmentService.findByStudent(student);
        List<Map<String, Object>> predictions = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            Course course = enrollment.getCourse();
            ScorePrediction prediction = predictionService.findLatestByStudentAndCourse(student, course);
            if (prediction != null) {
                Map<String, Object> pred = new HashMap<>();
                pred.put("courseId", course.getId());
                pred.put("courseName", course.getName());
                pred.put("predictedScore", prediction.getPredictedScore());
                pred.put("trend", prediction.getTrend());
                pred.put("confidenceInterval", 
                    prediction.getConfidenceLower() + " - " + prediction.getConfidenceUpper());
                predictions.add(pred);
            } else {
                // 没有预测数据时，显示平均分
                Map<String, Object> pred = new HashMap<>();
                pred.put("courseId", course.getId());
                pred.put("courseName", course.getName());
                pred.put("predictedScore", "待分析");
                pred.put("trend", "UNKNOWN");
                predictions.add(pred);
            }
        }
        dashboard.put("predictions", predictions);
        
        // 15. 下次考试提醒
        List<Exam> upcomingExams = examService.findUpcomingExams();
        List<Map<String, Object>> upcomingExamList = new ArrayList<>();
        for (Exam exam : upcomingExams) {
            // 检查学生是否参加该考试（通过课程选课）
            boolean isEnrolled = enrollments.stream()
                .anyMatch(e -> e.getCourse().getId().equals(exam.getCourse().getId()));
            if (isEnrolled) {
                Map<String, Object> examInfo = new HashMap<>();
                examInfo.put("examId", exam.getId());
                examInfo.put("examName", exam.getName());
                examInfo.put("examDate", exam.getExamDate());
                examInfo.put("courseName", exam.getCourse().getName());
                examInfo.put("daysLeft", java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(), exam.getExamDate()));
                upcomingExamList.add(examInfo);
            }
        }
        dashboard.put("upcomingExams", upcomingExamList);
        
        return Result.success(dashboard);
    }
    
    /**
     * 获取知识点雷达图专用数据
     * GET /api/dashboard/student/{studentId}/radar
     */
    @GetMapping("/{studentId}/radar")
    public Result<Map<String, Object>> getRadarData(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        List<KnowledgePointScoreDetail> details = kpScoreDetailService.findByStudent(student);
        
        // 按知识点去重，取最新值
        Map<String, BigDecimal> latestMap = new LinkedHashMap<>();
        for (KnowledgePointScoreDetail detail : details) {
            String kpName = detail.getKnowledgePoint().getName();
            latestMap.put(kpName, detail.getScoreRate());
        }
        
        List<String> indicators = new ArrayList<>(latestMap.keySet());
        List<BigDecimal> values = new ArrayList<>(latestMap.values());
        
        // 计算平均掌握度
        double avgMastery = values.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0);
        
        Map<String, Object> radarData = new HashMap<>();
        radarData.put("indicators", indicators);
        radarData.put("values", values);
        radarData.put("avgMastery", avgMastery);
        
        return Result.success(radarData);
    }
    
    /**
     * 获取成绩预测详情
     * GET /api/dashboard/student/{studentId}/prediction
     */
    @GetMapping("/{studentId}/prediction")
    public Result<Map<String, Object>> getScorePrediction(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> predictions = new ArrayList<>();
        
        // 获取学生所有选课
        List<Enrollment> enrollments = enrollmentService.findByStudent(student);
        
        for (Enrollment enrollment : enrollments) {
            Course course = enrollment.getCourse();
            ScorePrediction prediction = predictionService.findLatestByStudentAndCourse(student, course);
            
            Map<String, Object> pred = new HashMap<>();
            pred.put("courseId", course.getId());
            pred.put("courseName", course.getName());
            
            if (prediction != null) {
                pred.put("predictedScore", prediction.getPredictedScore());
                pred.put("trend", prediction.getTrend());
                pred.put("confidenceLower", prediction.getConfidenceLower());
                pred.put("confidenceUpper", prediction.getConfidenceUpper());
                pred.put("factors", prediction.getFactors());
                
                // 生成建议
                String suggestion = generateSuggestion(prediction.getTrend(), prediction.getPredictedScore());
                pred.put("suggestion", suggestion);
            } else {
                pred.put("predictedScore", null);
                pred.put("trend", "INSUFFICIENT_DATA");
                pred.put("suggestion", "数据不足，无法生成预测");
            }
            predictions.add(pred);
        }
        
        result.put("predictions", predictions);
        
        // 下次考试信息
        List<Exam> upcomingExams = examService.findUpcomingExams();
        if (!upcomingExams.isEmpty()) {
            Exam nextExam = upcomingExams.get(0);
            Map<String, Object> nextExamMap = new HashMap<>();
            nextExamMap.put("name", nextExam.getName());
            nextExamMap.put( "date", nextExam.getExamDate());
            nextExamMap.put( "course", nextExam.getCourse().getName());
            nextExamMap.put( "daysLeft", java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextExam.getExamDate()));
            result.put("nextExam",nextExamMap);
        }
        
        return Result.success(result);
    }
    
    /**
     * 获取学习建议（基于错题和薄弱知识点）
     * GET /api/dashboard/student/{studentId}/suggestions
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
            weakSuggestion.put("details", weakPoints.stream()
                .map(wp -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put( "name", wp.getKnowledgePoint().getName());
                    map.put( "mastery", wp.getMasteryLevel());
                    map.put( "suggestion", "建议加强练习，掌握度需提升至60%以上");
                    return map; 
                })
                .collect(Collectors.toList()));
            suggestions.add(weakSuggestion);
        }
        
        // 2. 基于活跃度生成建议
        Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
        if (activityScore != null && activityScore < 50) {
            Map<String, Object> activitySuggestion = new HashMap<>();
            activitySuggestion.put("type", "LOW_ACTIVITY");
            activitySuggestion.put("title", "🏃 活跃度提醒");
            activitySuggestion.put("content", "近期学习活跃度偏低，建议：");
            activitySuggestion.put("details",new ArrayList<>(Arrays.asList("每天登录系统查看学习任务", "按时提交作业，积极参与讨论", "观看教学视频，增加学习时长")));
            suggestions.add(activitySuggestion);
        }
        
        // 3. 基于作业完成情况生成建议
        List<Submission> submissions = submissionService.findByStudent(student);
        long lateCount = submissionService.countLateByStudent(student);
        if (lateCount > 0) {
            Map<String, Object> lateSuggestion = new HashMap<>();
            lateSuggestion.put("type", "LATE_SUBMISSION");
            lateSuggestion.put("title", "⏰ 作业提交提醒");
            lateSuggestion.put("content", "您有 " + lateCount + " 次作业迟交，建议提前规划时间，避免影响平时成绩");
            suggestions.add(lateSuggestion);
        }
        
        // 4. 基于成绩趋势生成建议
        String trend = examGradeService.getLatestTrend(studentId);
        if ("DECLINING".equals(trend)) {
            Map<String, Object> trendSuggestion = new HashMap<>();
            trendSuggestion.put("type", "DECLINING_TREND");
            trendSuggestion.put("title", "📉 成绩下滑提醒");
            trendSuggestion.put("content", "近期成绩呈下降趋势，建议：");
            trendSuggestion.put("details", new ArrayList<>(Arrays.asList( "分析错题原因，针对性复习",  "多与老师同学交流学习问题", "制定每日学习计划并坚持执行")));
            suggestions.add(trendSuggestion);
        } else if ("IMPROVING".equals(trend)) {
            Map<String, Object> trendSuggestion = new HashMap<>();
            trendSuggestion.put("type", "IMPROVING_TREND");
            trendSuggestion.put("title", "📈 进步鼓励");
            trendSuggestion.put("content", "成绩呈上升趋势，继续保持！建议挑战更高目标。");
            suggestions.add(trendSuggestion);
        }
        
        result.put("suggestions", suggestions);
        result.put("totalWeakPoints", weakPoints.size());
        result.put("activityLevel", activityScore != null ? 
            (activityScore >= 70 ? "HIGH" : activityScore >= 40 ? "MEDIUM" : "LOW") : "UNKNOWN");
        
        return Result.success(result);
    }
    
    private String generateSuggestion(String trend, BigDecimal predictedScore) {
        if ("IMPROVING".equals(trend)) {
            return "成绩呈上升趋势，保持当前学习状态，可适当挑战更高目标！";
        } else if ("DECLINING".equals(trend)) {
            return "成绩有所下降，建议加强薄弱知识点的复习，多做练习题。";
        } else if ("STABLE".equals(trend)) {
            if (predictedScore != null && predictedScore.doubleValue() >= 80) {
                return "成绩稳定优秀，继续保持！";
            } else if (predictedScore != null && predictedScore.doubleValue() >= 60) {
                return "成绩稳定，可以尝试突破到更高分数段。";
            } else {
                return "成绩稳定但偏低，建议加强基础知识的巩固。";
            }
        }
        return "继续努力，相信你能取得更好的成绩！";
    }

    /**
 * 手动刷新 Dashboard AI 分析报告
 * POST /api/dashboard/student/refresh/{studentId}
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
