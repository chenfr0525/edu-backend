package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.ActivityAlertService;
import com.edu.service.ActivityRecordService;
import com.edu.service.AiAnalysisReportService;
import com.edu.service.ClassService;
import com.edu.service.ClassWrongQuestionStatsService;
import com.edu.service.CourseService;
import com.edu.service.ExamGradeService;
import com.edu.service.ExamService;
import com.edu.service.HomeworkService;
import com.edu.service.StudentKnowledgeMasteryService;
import com.edu.service.StudentService;
import com.edu.service.SubmissionService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/teacher")
@RequiredArgsConstructor
public class TeacherDashboardController {
   private final ClassService classService;
    private final StudentService studentService;
    private final ExamGradeService examGradeService;
    private final ExamService examService;
    private final ClassWrongQuestionStatsService wrongQuestionStatsService;
    private final StudentKnowledgeMasteryService masteryService;
    private final ActivityRecordService activityRecordService;
    private final ActivityAlertService alertService;
    private final AiAnalysisReportService aiReportService;
    private final HomeworkService homeworkService;
    private final SubmissionService submissionService;
    private final CourseService courseService;
    
    /**
     * 班级教学看板
     * GET /api/dashboard/teacher/class/{classId}
     */
    @GetMapping("/class/{classId}")
    public Result<Map<String, Object>> getClassDashboard(@PathVariable Long classId) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // 1. 班级基本信息
        ClassInfo classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }

        Map<String, Object> classInfoMap = new HashMap<>();
        classInfoMap.put("id", classInfo.getId());
        classInfoMap.put("name", classInfo.getName());
        classInfoMap.put( "grade", classInfo.getGrade());
        classInfoMap.put("teacherName", classInfo.getTeacher() != null ? classInfo.getTeacher().getUser().getName() : "未分配");
        dashboard.put("classInfo", classInfoMap);

        // 2. 班级学生数量
        long studentCount = studentService.countByClassInfo(classInfo);
        dashboard.put("studentCount", studentCount);
        
        // 3. 最近一次考试成绩分布
        List<Exam> exams = examService.findByClassInfo(classInfo);
        if (!exams.isEmpty()) {
            Exam latestExam = exams.get(0);
            List<ExamGrade> grades = examGradeService.findByExam(latestExam);
            
            // 成绩分布统计
            Map<String, Integer> scoreDistribution = new LinkedHashMap<>();
            scoreDistribution.put("优秀(>=90)", 0);
            scoreDistribution.put("良好(80-89)", 0);
            scoreDistribution.put("中等(70-79)", 0);
            scoreDistribution.put("及格(60-69)", 0);
            scoreDistribution.put("不及格(<60)", 0);
            
            double totalScore = 0;
            List<Double> allScores = new ArrayList<>();
            for (ExamGrade grade : grades) {
                double score = grade.getScore().doubleValue();
                allScores.add(score);
                totalScore += score;
                if (score >= 90) scoreDistribution.put("优秀(>=90)", scoreDistribution.get("优秀(>=90)") + 1);
                else if (score >= 80) scoreDistribution.put("良好(80-89)", scoreDistribution.get("良好(80-89)") + 1);
                else if (score >= 70) scoreDistribution.put("中等(70-79)", scoreDistribution.get("中等(70-79)") + 1);
                else if (score >= 60) scoreDistribution.put("及格(60-69)", scoreDistribution.get("及格(60-69)") + 1);
                else scoreDistribution.put("不及格(<60)", scoreDistribution.get("不及格(<60)") + 1);
            }
            
            // 计算标准差（反映成绩离散程度）
            double avg = totalScore / grades.size();
            double variance = allScores.stream()
                .mapToDouble(s -> Math.pow(s - avg, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            Map<String, Object> latestExamMap = new HashMap<>();
            latestExamMap.put( "id", latestExam.getId());
            latestExamMap.put(  "name", latestExam.getName());
            latestExamMap.put("date", latestExam.getExamDate());
            latestExamMap.put( "fullScore", latestExam.getFullScore());
            dashboard.put("latestExam", latestExamMap);

        
            dashboard.put("scoreDistribution", scoreDistribution);
            dashboard.put("classAverageScore", grades.isEmpty() ? 0 : totalScore / grades.size());
            dashboard.put("highestScore", allScores.stream().max(Double::compare).orElse(0.0));
            dashboard.put("lowestScore", allScores.stream().min(Double::compare).orElse(0.0));
            dashboard.put("standardDeviation", Math.round(stdDev * 100) / 100.0);
            dashboard.put("passRate", grades.isEmpty() ? 0 : 
                (grades.stream().filter(g -> g.getScore().doubleValue() >= 60).count() * 100.0 / grades.size()));
        }
        
        // 4. 高频错题排行（前10）
        List<ClassWrongQuestionStats> wrongQuestions = 
            wrongQuestionStatsService.findTopWrongQuestions(classInfo, LocalDate.now());
        dashboard.put("topWrongQuestions", wrongQuestions.stream().limit(10).map(wq -> 
            {
                 Map<String, Object> map = new HashMap<>();
                map.put( "knowledgePointId", wq.getKnowledgePoint().getId());
                map.put( "knowledgePointName", wq.getKnowledgePoint().getName());
                map.put( "errorCount", wq.getErrorCount());
                map.put(  "rank", wq.getRankInClass());
                return map;
            }).collect(Collectors.toList()));
        
        // 5. 班级薄弱知识点（平均掌握度低于60%）
        List<Map<String, Object>> weakKnowledgePoints = findClassWeakPointsWithDetails(classInfo);
        dashboard.put("weakKnowledgePoints", weakKnowledgePoints);
        
        // 6. 活跃度监控
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        List<Long> lowActivityStudentIds = activityRecordService.findLowActivityStudents(weekAgo, 20.0);
        
        // 获取低活跃度学生详情
        List<Map<String, Object>> lowActivityStudents = new ArrayList<>();
        for (Long studentId : lowActivityStudentIds) {
            Student student = studentService.findById(studentId).orElse(null);
            if (student != null && classInfo.getId().equals(student.getClassInfo().getId())) {
                Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("studentId", student.getId());
                studentInfo.put("studentName", student.getUser().getName());
                studentInfo.put("studentNo", student.getStudentNo());
                studentInfo.put("activityScore", activityScore != null ? activityScore : 0);
                lowActivityStudents.add(studentInfo);
            }
        }
        dashboard.put("lowActivityStudents", lowActivityStudents);
        dashboard.put("lowActivityCount", lowActivityStudents.size());
        
        // 7. 未解决预警数量
        long unresolvedAlerts = alertService.findUnresolvedByClass(classInfo).size();
        dashboard.put("unresolvedAlerts", unresolvedAlerts);
        
        // 8. 严重预警学生
        List<ActivityAlert> criticalAlerts = alertService.findCriticalAlerts();
        List<Map<String, Object>> criticalStudents = new ArrayList<>();
        for (ActivityAlert alert : criticalAlerts) {
            if (alert.getClassInfo() != null && classInfo.getId().equals(alert.getClassInfo().getId())) {
                Map<String, Object> studentAlert = new HashMap<>();
                studentAlert.put("alertId", alert.getId());
                studentAlert.put("studentId", alert.getStudent().getId());
                studentAlert.put("studentName", alert.getStudent().getUser().getName());
                studentAlert.put("alertType", alert.getAlertType());
                studentAlert.put("alertLevel", alert.getAlertLevel());
                studentAlert.put("activityScore", alert.getActivityScore());
                studentAlert.put("threshold", alert.getThreshold());
                criticalStudents.add(studentAlert);
            }
        }
        dashboard.put("criticalAlerts", criticalStudents);
        
        // 9. 作业完成情况统计
        List<Student> students = studentService.findByClassInfo(classInfo);
        long totalHomeworkCount = 0;
        long totalSubmittedCount = 0;
        double totalHomeworkAvgScore = 0;
        
        for (Student student : students) {
            List<Submission> submissions = submissionService.findByStudent(student);
            totalHomeworkCount += submissions.size();
            totalSubmittedCount += submissions.stream()
                .filter(s -> "GRADED".equals(s.getStatus()))
                .count();
            Double avgScore = submissionService.getStudentAverageScore(student.getId());
            if (avgScore != null) {
                totalHomeworkAvgScore += avgScore;
            }
        }
        Map<String, Object> classHomeworkStatsMap = new HashMap<>();
        classHomeworkStatsMap.put("totalHomework", totalHomeworkCount);
        classHomeworkStatsMap.put("submittedCount", totalSubmittedCount);
        classHomeworkStatsMap.put("submissionRate", totalHomeworkCount > 0 ? 
            (totalSubmittedCount * 100.0 / totalHomeworkCount) : 0);
        classHomeworkStatsMap.put("averageScore", students.isEmpty() ? 0 : totalHomeworkAvgScore / students.size());
        dashboard.put("classHomeworkStats", classHomeworkStatsMap);
        
        // 10. AI 生成的班级学情总结报告
        AiAnalysisReport latestReport = aiReportService.findLatestReport("CLASS", classId, "COMPREHENSIVE");
        if (latestReport != null) {
            dashboard.put("aiSummary", latestReport.getSummary());
            dashboard.put("aiSuggestions", latestReport.getSuggestions());
            dashboard.put("aiReportDate", latestReport.getCreatedAt());
        } else {
            dashboard.put("aiSummary", "暂无AI分析报告，请先上传足够的作业和考试数据");
            dashboard.put("aiSuggestions", "建议上传历次考试成绩和作业数据，系统将自动生成学情分析");
        }
        
        return Result.success(dashboard);
    }
    
    /**
     * 获取班级成绩趋势
     * GET /api/dashboard/teacher/class/{classId}/trend
     */
    @GetMapping("/class/{classId}/trend")
    public Result<Map<String, Object>> getClassScoreTrend(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }
        
        List<Exam> exams = examService.findByClassInfo(classInfo);
        
        List<String> examNames = new ArrayList<>();
        List<Double> avgScores = new ArrayList<>();
        List<Double> highestScores = new ArrayList<>();
        List<Double> lowestScores = new ArrayList<>();
        List<Double> passRates = new ArrayList<>();
        
        for (Exam exam : exams) {
            List<ExamGrade> grades = examGradeService.findByExam(exam);
            if (!grades.isEmpty()) {
                double avg = grades.stream()
                    .mapToDouble(g -> g.getScore().doubleValue())
                    .average()
                    .orElse(0);
                double highest = grades.stream()
                    .mapToDouble(g -> g.getScore().doubleValue())
                    .max()
                    .orElse(0);
                double lowest = grades.stream()
                    .mapToDouble(g -> g.getScore().doubleValue())
                    .min()
                    .orElse(0);
                long passCount = grades.stream()
                    .filter(g -> g.getScore().doubleValue() >= 60)
                    .count();
                
                examNames.add(exam.getName());
                avgScores.add(Math.round(avg * 100) / 100.0);
                highestScores.add(highest);
                lowestScores.add(lowest);
                passRates.add(Math.round((passCount * 100.0 / grades.size()) * 100) / 100.0);
            }
        }
        
        Map<String, Object> trend = new HashMap<>();
        trend.put("examNames", examNames);
        trend.put("avgScores", avgScores);
        trend.put("highestScores", highestScores);
        trend.put("lowestScores", lowestScores);
        trend.put("passRates", passRates);
        
        return Result.success(trend);
    }
    
    /**
     * 获取班级活跃度趋势
     * GET /api/dashboard/teacher/class/{classId}/activity
     */
    @GetMapping("/class/{classId}/activity")
    public Result<Map<String, Object>> getClassActivityTrend(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }
        
        List<Student> students = studentService.findByClassInfo(classInfo);
        
        // 统计最近30天的活跃度
        List<Map<String, Object>> dailyActivity = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double totalActivity = 0;
            int activeCount = 0;
            
            for (Student student : students) {
                List<ActivityRecord> records = activityRecordService
                    .findByStudentAndDateRange(student, date, date);
                if (!records.isEmpty()) {
                    activeCount++;
                    double dayScore = records.stream()
                        .mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0)
                        .sum();
                    totalActivity += dayScore;
                }
            }
            
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("avgActivity", students.isEmpty() ? 0 : 
                Math.round((totalActivity / students.size()) * 100) / 100.0);
            dayData.put("activeRate", students.isEmpty() ? 0 : 
                Math.round((activeCount * 100.0 / students.size()) * 100) / 100.0);
            dayData.put("activeCount", activeCount);
            dailyActivity.add(dayData);
        }
        
        // 本周和上周对比
        LocalDate thisWeekStart = today.minusDays(7);
        LocalDate lastWeekStart = today.minusDays(14);
        
        double thisWeekAvg = dailyActivity.stream()
            .filter(d -> LocalDate.parse((String) d.get("date")).isAfter(thisWeekStart))
            .mapToDouble(d -> (double) d.get("avgActivity"))
            .average()
            .orElse(0);
        double lastWeekAvg = dailyActivity.stream()
            .filter(d -> {
                LocalDate date = LocalDate.parse((String) d.get("date"));
                return date.isAfter(lastWeekStart) && date.isBefore(thisWeekStart);
            })
            .mapToDouble(d -> (double) d.get("avgActivity"))
            .average()
            .orElse(0);
        
        Map<String, Object> result = new HashMap<>();
        result.put("dailyActivity", dailyActivity);
        result.put("thisWeekAvgActivity", Math.round(thisWeekAvg * 100) / 100.0);
        result.put("lastWeekAvgActivity", Math.round(lastWeekAvg * 100) / 100.0);
        result.put("activityChange", Math.round((thisWeekAvg - lastWeekAvg) * 100) / 100.0);
        
        // 低活跃度学生名单（最近7天）
        List<Long> lowActivityStudentIds = activityRecordService.findLowActivityStudents(today.minusDays(7), 20.0);
        List<Map<String, Object>> lowActivityDetails = new ArrayList<>();
        for (Long studentId : lowActivityStudentIds) {
            Student student = studentService.findById(studentId).orElse(null);
            if (student != null && classInfo.getId().equals(student.getClassInfo().getId())) {
                Double activityScore = activityRecordService.getStudentTotalActivityScore(studentId);
                Integer studyDuration = activityRecordService.getStudentStudyDuration(studentId, today.minusDays(7));
                Map<String, Object> detail = new HashMap<>();
                detail.put("studentId", student.getId());
                detail.put("studentName", student.getUser().getName());
                detail.put("activityScore", activityScore != null ? activityScore : 0);
                detail.put("studyDuration", studyDuration != null ? studyDuration : 0);
                lowActivityDetails.add(detail);
            }
        }
        result.put("lowActivityStudents", lowActivityDetails);
        
        return Result.success(result);
    }
    
    /**
     * 获取班级知识点掌握度分析
     * GET /api/dashboard/teacher/class/{classId}/knowledge-mastery
     */
    @GetMapping("/class/{classId}/knowledge-mastery")
    public Result<Map<String, Object>> getClassKnowledgeMastery(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }
        
        List<Student> students = studentService.findByClassInfo(classInfo);
        
        // 统计各知识点平均掌握度
        Map<String, List<Double>> kpScores = new HashMap<>();
        Map<String, Long> kpIds = new HashMap<>();
        
        for (Student student : students) {
            List<StudentKnowledgeMastery> masteries = masteryService.findByStudent(student);
            for (StudentKnowledgeMastery mastery : masteries) {
                String kpName = mastery.getKnowledgePoint().getName();
                kpScores.computeIfAbsent(kpName, k -> new ArrayList<>())
                    .add(mastery.getMasteryLevel().doubleValue());
                kpIds.put(kpName, mastery.getKnowledgePoint().getId());
            }
        }
        
        // 计算平均掌握度和标准差
        List<Map<String, Object>> knowledgeStats = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : kpScores.entrySet()) {
            String kpName = entry.getKey();
            List<Double> scores = entry.getValue();
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = scores.stream()
                .mapToDouble(s -> Math.pow(s - avg, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            Map<String, Object> stat = new HashMap<>();
            stat.put("knowledgePointId", kpIds.get(kpName));
            stat.put("knowledgePointName", kpName);
            stat.put("avgMastery", Math.round(avg * 100) / 100.0);
            stat.put("stdDeviation", Math.round(stdDev * 100) / 100.0);
            stat.put("weakLevel", avg < 60 ? "严重薄弱" : avg < 70 ? "需加强" : avg < 80 ? "良好" : "优秀");
            knowledgeStats.add(stat);
        }
        
        // 按掌握度排序
        knowledgeStats.sort((a, b) -> Double.compare(
            (double) a.get("avgMastery"), 
            (double) b.get("avgMastery")
        ));
        
        Map<String, Object> result = new HashMap<>();
        result.put("knowledgeStats", knowledgeStats);
        result.put("totalKnowledgePoints", knowledgeStats.size());
        result.put("weakPointsCount", knowledgeStats.stream()
            .filter(k -> (double) k.get("avgMastery") < 60)
            .count());
        result.put("strongPointsCount", knowledgeStats.stream()
            .filter(k -> (double) k.get("avgMastery") >= 80)
            .count());
        
        return Result.success(result);
    }
    
    /**
     * 获取班级整体学情摘要
     * GET /api/dashboard/teacher/class/{classId}/summary
     */
    @GetMapping("/class/{classId}/summary")
    public Result<Map<String, Object>> getClassSummary(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return Result.error("班级不存在");
        }
        
        List<Student> students = studentService.findByClassInfo(classInfo);
        
        // 计算各种指标
        double totalExamScore = 0;
        double totalHomeworkScore = 0;
        int examCount = 0;
        int studentWithExam = 0;
        
        for (Student student : students) {
            Double examAvg = examGradeService.getStudentAverageScore(student.getId());
            if (examAvg != null) {
                totalExamScore += examAvg;
                studentWithExam++;
            }
            
            Double homeworkAvg = submissionService.getStudentAverageScore(student.getId());
            if (homeworkAvg != null) {
                totalHomeworkScore += homeworkAvg;
            }
            examCount++;
        }
        
        // 成绩分布
        List<String> gradeDistribution = new ArrayList<>();
        for (Student student : students) {
            Double avgScore = examGradeService.getStudentAverageScore(student.getId());
            if (avgScore != null) {
                if (avgScore >= 90) gradeDistribution.add("A");
                else if (avgScore >= 80) gradeDistribution.add("B");
                else if (avgScore >= 70) gradeDistribution.add("C");
                else if (avgScore >= 60) gradeDistribution.add("D");
                else gradeDistribution.add("F");
            }
        }
        
        long aCount = gradeDistribution.stream().filter(g -> g.equals("A")).count();
        long bCount = gradeDistribution.stream().filter(g -> g.equals("B")).count();
        long cCount = gradeDistribution.stream().filter(g -> g.equals("C")).count();
        long dCount = gradeDistribution.stream().filter(g -> g.equals("D")).count();
        long fCount = gradeDistribution.stream().filter(g -> g.equals("F")).count();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("className", classInfo.getName());
        summary.put("studentCount", students.size());
        summary.put("averageExamScore", studentWithExam > 0 ? 
            Math.round((totalExamScore / studentWithExam) * 100) / 100.0 : 0);
        summary.put("averageHomeworkScore", examCount > 0 ? 
            Math.round((totalHomeworkScore / examCount) * 100) / 100.0 : 0);

            Map<String, Object> gradeDistributionMap = new HashMap<>();
            gradeDistributionMap.put( "A", aCount);
            gradeDistributionMap.put( "B", bCount);
            gradeDistributionMap.put(  "C", cCount);
            gradeDistributionMap.put( "D", dCount);
            gradeDistributionMap.put( "F", fCount);
        summary.put("gradeDistribution",gradeDistributionMap);
        
        // 生成文字总结
        String textSummary = generateTextSummary(classInfo.getName(), students.size(), 
            totalExamScore / studentWithExam, totalHomeworkScore / examCount,
            aCount, bCount, cCount, dCount, fCount);
        summary.put("textSummary", textSummary);
        
        return Result.success(summary);
    }
    
    /**
     * 获取班级薄弱知识点详情（带学生列表）
     */
    private List<Map<String, Object>> findClassWeakPointsWithDetails(ClassInfo classInfo) {
        List<Student> students = studentService.findByClassInfo(classInfo);
        
        // 统计各知识点平均掌握度
        Map<Long, Map<String, Object>> kpStats = new HashMap<>();
        
        for (Student student : students) {
            List<StudentKnowledgeMastery> masteries = masteryService.findByStudent(student);
            for (StudentKnowledgeMastery mastery : masteries) {
                Long kpId = mastery.getKnowledgePoint().getId();
                kpStats.putIfAbsent(kpId, new HashMap<>());
                Map<String, Object> stat = kpStats.get(kpId);
                stat.put("name", mastery.getKnowledgePoint().getName());
                stat.put("id", kpId);
                
                List<Double> scores = (List<Double>) stat.getOrDefault("scores", new ArrayList<Double>());
                scores.add(mastery.getMasteryLevel().doubleValue());
                stat.put("scores", scores);
            }
        }
        
        // 计算平均并筛选低于60%的知识点
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> entry : kpStats.entrySet()) {
            Map<String, Object> stat = entry.getValue();
            List<Double> scores = (List<Double>) stat.get("scores");
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            if (avg < 60) {
                Map<String, Object> weak = new HashMap<>();
                weak.put("knowledgePointId", entry.getKey());
                weak.put("knowledgePointName", stat.get("name"));
                weak.put("avgMastery", Math.round(avg * 100) / 100.0);
                weak.put("studentCount", scores.size());
                weak.put("affectedRate", Math.round((scores.size() * 100.0 / students.size()) * 100) / 100.0);
                weakPoints.add(weak);
            }
        }
        
        // 按掌握度排序（最弱的排最前）
        weakPoints.sort((a, b) -> Double.compare(
            (double) a.get("avgMastery"),
            (double) b.get("avgMastery")
        ));
        
        return weakPoints;
    }
    
    private String generateTextSummary(String className, int studentCount, 
                                       double avgExamScore, double avgHomeworkScore,
                                       long aCount, long bCount, long cCount, long dCount, long fCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(" 班级学情总结：\n");
        sb.append("班级共 ").append(studentCount).append(" 人。\n");
        
        // 成绩评价
        if (avgExamScore >= 80) {
            sb.append("班级整体成绩优秀，平均分 ").append(String.format("%.1f", avgExamScore)).append(" 分。");
        } else if (avgExamScore >= 70) {
            sb.append("班级整体成绩良好，平均分 ").append(String.format("%.1f", avgExamScore)).append(" 分。");
        } else if (avgExamScore >= 60) {
            sb.append("班级整体成绩中等，平均分 ").append(String.format("%.1f", avgExamScore)).append(" 分，有提升空间。");
        } else {
            sb.append("班级整体成绩偏低，平均分 ").append(String.format("%.1f", avgExamScore)).append(" 分，需要加强辅导。");
        }
        
        // 成绩分布
        sb.append("\n成绩分布：A(").append(aCount).append("人) B(").append(bCount)
          .append("人) C(").append(cCount).append("人) D(").append(dCount)
          .append("人) F(").append(fCount).append("人)。");
        
        // 作业情况
        if (avgHomeworkScore >= 80) {
            sb.append("\n作业完成质量优秀，平均分 ").append(String.format("%.1f", avgHomeworkScore)).append(" 分。");
        } else if (avgHomeworkScore >= 60) {
            sb.append("\n作业完成情况良好，平均分 ").append(String.format("%.1f", avgHomeworkScore)).append(" 分。");
        } else {
            sb.append("\n作业完成质量有待提高，平均分 ").append(String.format("%.1f", avgHomeworkScore)).append(" 分。");
        }
        
        // 改进建议
        if (fCount > studentCount * 0.2) {
            sb.append("\n建议重点关注不及格学生，安排课后辅导。");
        }
        if (avgHomeworkScore < avgExamScore) {
            sb.append("\n作业成绩低于考试成绩，建议加强作业练习质量。");
        }
        
        return sb.toString();
    }

}
