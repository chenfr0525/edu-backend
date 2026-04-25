package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Course;
import com.edu.domain.Homework;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.domain.Submission;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.ClassStatisticsDTO;
import com.edu.domain.dto.HomeworkStatisticsCards;
import com.edu.domain.dto.HomeworkTrendData;
import com.edu.domain.dto.KnowledgePointMasteryDTO;
import com.edu.domain.dto.MySubmissionInfo;
import com.edu.domain.dto.ScoreAnalysisDTO;
import com.edu.domain.dto.ScoreDistributionDTO;
import com.edu.domain.dto.StudentHomeworkDTO;
import com.edu.domain.dto.StudentHomeworkDetailDTO;
import com.edu.repository.HomeworkRepository;
import com.edu.repository.KnowledgePointRepository;
import com.edu.repository.SubmissionRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentHomeworkAnalysisService {
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final SemesterService semesterService;
    private final AiAnalysisReportService aiReportService;
    private final DeepSeekService deepSeekService;
    private final KnowledgePointRepository knowledgePointRepository;

    // ==================== 公共方法 ====================
    
    public List<StudentHomeworkDTO> getStudentHomeworkList(Long studentId, Long courseId, String status) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        Semester currentSemester = getCurrentSemester();
        if (currentSemester == null) {
            return Collections.emptyList();
        }
        
        List<Homework> homeworks;
        if (courseId != null && courseId > 0) {
            homeworks = homeworkRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            homeworks = homeworkRepository.findByStudentIdAndSemesterId(studentId, currentSemester.getId());
        }
        
        if (status != null && !status.isEmpty()) {
            homeworks = homeworks.stream()
                    .filter(h -> h.getStatus().equals(status))
                    .collect(Collectors.toList());
        }
        
        List<StudentHomeworkDTO> result = new ArrayList<>();
        for (Homework homework : homeworks) {
            result.add(convertToStudentHomeworkDTO(homework, studentId));
        }
        return result;
    }
    
    @Transactional
    public StudentHomeworkDetailDTO getStudentHomeworkDetail(Long studentId, Long homeworkId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        StudentHomeworkDetailDTO detail = new StudentHomeworkDetailDTO();
        
        detail.setId(homework.getId());
        detail.setName(homework.getName());
        detail.setDescription(homework.getDescription());
        detail.setCourseName(homework.getCourse().getName());
        detail.setCourseId(homework.getCourse().getId());
        detail.setTotalScore(homework.getTotalScore());
        detail.setQuestionCount(homework.getQuestionCount());
        detail.setStatus(homework.getStatus().toString());
        detail.setDeadline(homework.getDeadline());
        detail.setCreatedAt(homework.getCreatedAt());
        
        detail.setMySubmission(getMySubmissionInfo(studentId, homework));
        detail.setClassStats(getClassStatistics(homework));
        detail.setKnowledgePointAnalysis(getKnowledgePointAnalysis(studentId, homework));
        detail.setScoreAnalysis(getScoreAnalysis(studentId, homework));
        detail.setAiSuggestion(getOrCreateAiSuggestion(student, homework));
        
        return detail;
    }
    
    public HomeworkStatisticsCards getStudentStatisticsCards(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        HomeworkStatisticsCards cards = new HomeworkStatisticsCards();
        
        List<Submission> gradedSubmissions = submissionRepository.findGradedByStudentId(studentId);
        
        Semester currentSemester = getCurrentSemester();
        long totalHomework = 0;
        if (currentSemester != null) {
            totalHomework = homeworkRepository.findByStudentIdAndSemesterId(studentId, currentSemester.getId()).size();
        }else{
            totalHomework = homeworkRepository.findByStudentId(student.getId()).size();
        }
        cards.setTotalCount(totalHomework);
        cards.setCompletedCount((long) gradedSubmissions.size());
        
        double avgScore = gradedSubmissions.stream()
                .filter(s -> s.getScore() != null)
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0);
        cards.setAvgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        
        cards.setCompletionRate(totalHomework > 0 ?
                BigDecimal.valueOf(gradedSubmissions.size() * 100.0 / totalHomework).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        long onTimeCount = gradedSubmissions.stream()
                .filter(s -> s.getSubmissionLateMinutes() == null || s.getSubmissionLateMinutes() == 0)
                .count();
        cards.setOnTimeRate(gradedSubmissions.size() > 0 ?
                BigDecimal.valueOf(onTimeCount * 100.0 / gradedSubmissions.size()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        long aboveAvgCount = 0;
        for (Submission sub : gradedSubmissions) {
            Homework homework = sub.getHomework();
            BigDecimal classAvg = homework.getAvgScore();
            if (classAvg != null && sub.getScore() != null && sub.getScore() > classAvg.doubleValue()) {
                aboveAvgCount++;
            }
        }
        cards.setAboveAvgCount(aboveAvgCount);
        cards.setAboveAvgRate(gradedSubmissions.size() > 0 ?
                BigDecimal.valueOf(aboveAvgCount * 100.0 / gradedSubmissions.size()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        return cards;
    }
    
    public HomeworkTrendData getStudentTrendData(Long studentId, Long courseId) {
        HomeworkTrendData trendData = new HomeworkTrendData();
        
        List<Submission> submissions;
        String courseName = "全部课程";
        
        if (courseId != null && courseId > 0) {
            submissions = submissionRepository.findByStudentIdAndCourseIdGraded(studentId, courseId);
            Course course = courseService.findById(courseId).orElse(null);
            if (course != null) {
                courseName = course.getName();
            }
        } else {
            submissions = submissionRepository.findGradedByStudentId(studentId);
        }
        
        submissions.sort(Comparator.comparing(Submission::getSubmittedAt));
        
        List<String> homeworkNames = new ArrayList<>();
        List<BigDecimal> myScores = new ArrayList<>();
        List<BigDecimal> classAvgs = new ArrayList<>();
        
        for (Submission sub : submissions) {
            homeworkNames.add(sub.getHomework().getName());
            myScores.add(BigDecimal.valueOf(sub.getScore()));
            BigDecimal classAvg = sub.getHomework().getAvgScore();
            classAvgs.add(classAvg != null ? classAvg : BigDecimal.ZERO);
        }
        
        trendData.setHomeworkNames(homeworkNames);
        trendData.setMyScores(myScores);
        trendData.setClassAvgs(classAvgs);
        trendData.setCourseName(courseName);
        
        if (myScores.size() >= 2) {
            BigDecimal first = myScores.get(0);
            BigDecimal last = myScores.get(myScores.size() - 1);
            int compare = last.compareTo(first);
            if (compare > 0) {
                trendData.setTrend("上升");
                trendData.setTrendValue(last.subtract(first));
            } else if (compare < 0) {
                trendData.setTrend("下降");
                trendData.setTrendValue(first.subtract(last));
            } else {
                trendData.setTrend("稳定");
                trendData.setTrendValue(BigDecimal.ZERO);
            }
        } else {
            trendData.setTrend("数据不足");
            trendData.setTrendValue(BigDecimal.ZERO);
        }
        
        return trendData;
    }
    
    public Map<String, Object> getStudentOverallAnalysis(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        AiAnalysisReport cachedReport = aiReportService.findLatestReport("STUDENT", studentId, "HOMEWORK_OVERALL");
        
        if (cachedReport != null && cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的整体作业分析，学生ID: {}", studentId);
            Map<String, Object> result = new HashMap<>();
            result.put("summary", cachedReport.getSummary());
            result.put("suggestions", cachedReport.getSuggestions());
            result.put("createdAt", cachedReport.getCreatedAt());
            result.put("fromCache", true);
            return result;
        }

        List<Submission> submissions = submissionRepository.findGradedByStudentId(studentId);
        if (submissions.isEmpty()) {
            return createEmptyAnalysisResponse();
        }

        log.info("调用 AI 生成整体作业分析，学生ID: {}", studentId);
        try {
            JSONObject dataJson = buildHomeworkOverallAiData(student, submissions);
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(dataJson.toJSONString(), "某个学生作业整体分析");

            if (aiResponse != null && aiResponse.getSummary() != null) {
                saveHomeworkOverallReport(student, submissions, aiResponse);
                
                Map<String, Object> result = new HashMap<>();
                result.put("summary", aiResponse.getSummary());
                result.put("suggestions", String.join("\n", aiResponse.getSuggestions()));
                result.put("totalHomework", submissions.size());
                result.put("avgScore", submissions.stream().mapToDouble(Submission::getScore).average().orElse(0));
                result.put("fromCache", false);
                return result;
            }
        } catch (Exception e) {
            log.error("调用 AI 生成整体作业分析失败", e);
        }
        
        return generateStudentOverallAnalysis(student);
    }
    
    public AiSuggestionDTO refreshAiAnalysis(Long studentId, Long homeworkId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        String cacheKey = "HOMEWORK_" + homeworkId;
        List<AiAnalysisReport> oldReports = aiReportService.findByTarget("STUDENT", studentId);
        for (AiAnalysisReport report : oldReports) {
            if (cacheKey.equals(report.getReportType())) {
                aiReportService.deleteById(report.getId());
                log.info("删除旧的 AI 报告，ID: {}", report.getId());
            }
        }
        
        return getOrCreateAiSuggestion(student, homework);
    }
    
    // ==================== AI 相关核心方法 ====================
    
    private AiSuggestionDTO getOrCreateAiSuggestion(Student student, Homework homework) {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();

        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(student.getId(), homework.getId());
        
        if (!submission.isPresent() || submission.get().getScore() == null) {
            suggestion.setSummary("作业尚未批改，暂无法生成分析建议");
            suggestion.setStrengths(Arrays.asList("待批改后查看"));
            suggestion.setWeaknesses(Arrays.asList("待批改后查看"));
            suggestion.setSuggestions(Arrays.asList("请耐心等待老师批改"));
            return suggestion;
        }

        String cacheKey = "HOMEWORK_" + homework.getId();
        AiAnalysisReport cached = aiReportService.findLatestReport("STUDENT", student.getId(), cacheKey);
        
        if (cached != null && cached.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的作业AI分析，学生ID: {}, 作业ID: {}", student.getId(), homework.getId());
            return parseReportToAiSuggestion(cached);
        }
        
        log.info("调用 AI 生成作业分析，学生ID: {}, 作业ID: {}", student.getId(), homework.getId());

        try {
            Submission sub = submission.get();
            JSONObject dataJson = buildHomeworkAiData(student, homework, sub);
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(dataJson.toJSONString(), "某个学生作业分析");

            if (aiResponse != null && aiResponse.getSummary() != null) {
                suggestion.setSummary(aiResponse.getSummary());
                suggestion.setSuggestions(aiResponse.getSuggestions());
                suggestion.setStrengths(aiResponse.getStrengths() != null ? aiResponse.getStrengths() : extractStrengthsFromSummary(aiResponse.getSummary()));
                suggestion.setWeaknesses(aiResponse.getWeaknesses() != null ? aiResponse.getWeaknesses() : extractWeaknessesFromSummary(aiResponse.getSummary()));
                
                saveHomeworkAiReport(student, homework, sub, aiResponse);
            } else {
                suggestion = getFallbackAiSuggestion(sub, homework.getAvgScore());
            }
        } catch (Exception e) {
            log.error("调用 AI 生成作业分析失败，使用降级方案", e);
            suggestion = getFallbackAiSuggestion(submission.get(), homework.getAvgScore());
        }   
        return suggestion;
    }
    
    // ==================== 数据构建方法 ====================
    
    private JSONObject buildHomeworkAiData(Student student, Homework homework, Submission submission) {
        JSONObject data = new JSONObject();
        data.put("studentName", student.getUser().getName());
        data.put("homeworkName", homework.getName());
        data.put("courseName", homework.getCourse().getName());
        data.put("myScore", submission.getScore());
        data.put("totalScore", homework.getTotalScore());
        data.put("classAvgScore", homework.getAvgScore());
        data.put("isLate", submission.getSubmissionLateMinutes() != null && submission.getSubmissionLateMinutes() > 0);
        data.put("knowledgePointScores", submission.getKnowledgePointScores());
        return data;
    }
    
    private JSONObject buildHomeworkOverallAiData(Student student, List<Submission> submissions) {
        JSONObject data = new JSONObject();
        data.put("studentName", student.getUser().getName());
        
        List<JSONObject> homeworkList = new ArrayList<>();
        for (Submission sub : submissions) {
            JSONObject hw = new JSONObject();
            hw.put("name", sub.getHomework().getName());
            hw.put("score", sub.getScore());
            hw.put("classAvg", sub.getHomework().getAvgScore());
            homeworkList.add(hw);
        }
        data.put("homeworks", homeworkList);
        
        double avgScore = submissions.stream().mapToDouble(Submission::getScore).average().orElse(0);
        data.put("avgScore", avgScore);
        
        long aboveAvgCount = submissions.stream()
                .filter(s -> s.getHomework().getAvgScore() != null && 
                            s.getScore() > s.getHomework().getAvgScore().doubleValue())
                .count();
        data.put("aboveAvgCount", aboveAvgCount);
        
        long onTimeCount = submissions.stream()
                .filter(s -> s.getSubmissionLateMinutes() == null || s.getSubmissionLateMinutes() == 0)
                .count();
        data.put("onTimeRate", submissions.isEmpty() ? 0 : onTimeCount * 100.0 / submissions.size());
        
        return data;
    }
    
    // ==================== 保存到数据库 ====================
    
    private void saveHomeworkAiReport(Student student, Homework homework, Submission submission, AiSuggestionDTO aiResponse) {
        try {
            Semester currentSemester = getCurrentSemester();
            String cacheKey = "HOMEWORK_" + homework.getId();
            
            JSONObject analysisData = new JSONObject();
            analysisData.put("homeworkId", homework.getId());
            analysisData.put("homeworkName", homework.getName());
            analysisData.put("myScore", submission.getScore());
            analysisData.put("classAvg", homework.getAvgScore());
            analysisData.put("knowledgePointScores", submission.getKnowledgePointScores());
            analysisData.put("aiResponse", aiResponse);
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            
            AiAnalysisReport report = AiAnalysisReport.builder()
                .targetType("STUDENT")
                .targetId(student.getId())
                .semester(currentSemester)
                .reportType(cacheKey)
                .analysisData(analysisData.toJSONString())
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .createdAt(LocalDateTime.now())
                .build();
            
            aiReportService.save(report);
            log.info("保存作业 AI 报告成功，学生ID: {}, 作业ID: {}", student.getId(), homework.getId());
        } catch (Exception e) {
            log.error("保存作业 AI 报告失败", e);
        }
    }
    
    private void saveHomeworkOverallReport(Student student, List<Submission> submissions, AiSuggestionDTO aiResponse) {
        try {
            Semester currentSemester = getCurrentSemester();
            
            JSONObject analysisData = new JSONObject();
            analysisData.put("totalHomework", submissions.size());
            analysisData.put("avgScore", submissions.stream().mapToDouble(Submission::getScore).average().orElse(0));
            analysisData.put("aiResponse", aiResponse);
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            
            AiAnalysisReport report = AiAnalysisReport.builder()
                .targetType("STUDENT")
                .targetId(student.getId())
                .semester(currentSemester)
                .reportType("HOMEWORK_OVERALL")
                .analysisData(analysisData.toJSONString())
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .createdAt(LocalDateTime.now())
                .build();
            
            aiReportService.save(report);
            log.info("保存整体作业 AI 报告成功，学生ID: {}", student.getId());
        } catch (Exception e) {
            log.error("保存整体作业 AI 报告失败", e);
        }
    }
    
    // ==================== 解析方法 ====================
    
    private AiSuggestionDTO parseReportToAiSuggestion(AiAnalysisReport report) {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();
        suggestion.setSummary(report.getSummary());
        
        List<String> suggestions = new ArrayList<>();
        if (report.getSuggestions() != null) {
            suggestions = Arrays.asList(report.getSuggestions().split("\n"));
        }
        suggestion.setSuggestions(suggestions);
        
        List<String> strengths = extractStrengthsFromSummary(report.getSummary());
        List<String> weaknesses = extractWeaknessesFromSummary(report.getSummary());
        
        suggestion.setStrengths(strengths.isEmpty() ? Arrays.asList("待补充") : strengths);
        suggestion.setWeaknesses(weaknesses.isEmpty() ? Arrays.asList("待补充") : weaknesses);
        
        return suggestion;
    }
    
    private List<String> extractStrengthsFromSummary(String summary) {
        List<String> strengths = new ArrayList<>();
        if (summary == null) {
            strengths.add("暂无数据");
            return strengths;
        }
        if (summary.contains("优秀") || summary.contains("良好") || summary.contains("扎实")) {
            strengths.add("基础知识掌握较好");
            strengths.add("学习态度认真");
        } else if (summary.contains("进步")) {
            strengths.add("学习有进步");
        } else {
            strengths.add("有提升空间");
        }
        return strengths;
    }
    
    private List<String> extractWeaknessesFromSummary(String summary) {
        List<String> weaknesses = new ArrayList<>();
        if (summary == null) {
            weaknesses.add("暂无数据");
            return weaknesses;
        }
        if (summary.contains("薄弱") || summary.contains("不足") || summary.contains("需要加强")) {
            weaknesses.add("部分知识点掌握不牢固");
        } else {
            weaknesses.add("无明显薄弱点");
        }
        return weaknesses;
    }
    
    // ==================== 降级方案 ====================
    
    private AiSuggestionDTO getFallbackAiSuggestion(Submission submission, BigDecimal classAvg) {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();
        Double score = submission.getScore();
        
        if (score >= 90) {
            suggestion.setSummary("本次作业表现优秀！知识点掌握扎实，解题思路清晰。");
            suggestion.setStrengths(Arrays.asList("基础知识牢固", "解题规范", "思路清晰"));
            suggestion.setWeaknesses(Arrays.asList("可挑战更高难度题目"));
            suggestion.setSuggestions(Arrays.asList("继续保持学习节奏", "尝试帮助其他同学", "挑战拓展题"));
        } else if (score >= 75) {
            suggestion.setSummary("本次作业表现良好，基础知识点掌握不错，部分细节需要完善。");
            suggestion.setStrengths(Arrays.asList("基础题正确率高", "按时提交"));
            suggestion.setWeaknesses(Arrays.asList("部分综合题扣分", "细节处理待加强"));
            suggestion.setSuggestions(Arrays.asList("复习错题相关知识点", "多做同类题型练习", "参考优秀作业"));
        } else if (score >= 60) {
            suggestion.setSummary("本次作业成绩及格，但仍有提升空间，建议加强薄弱环节。");
            suggestion.setStrengths(Arrays.asList("参与度高", "完成度良好"));
            suggestion.setWeaknesses(Arrays.asList("基础概念理解不深", "解题步骤不完整"));
            suggestion.setSuggestions(Arrays.asList("重新复习课堂笔记", "观看教学视频", "向老师/同学请教"));
        } else {
            suggestion.setSummary("本次作业成绩不理想，需要重点关注基础知识的巩固。");
            suggestion.setStrengths(Arrays.asList("愿意尝试完成作业"));
            suggestion.setWeaknesses(Arrays.asList("基础概念模糊", "解题方法不当", "练习量不足"));
            suggestion.setSuggestions(Arrays.asList("回顾课堂内容", "完成基础练习题", "寻求老师辅导", "与同学组成学习小组"));
        }
        
        if (classAvg != null) {
            if (score > classAvg.doubleValue()) {
                suggestion.setSummary(suggestion.getSummary() + " 你的成绩高于班级平均分" + 
                        String.format("%.1f", Math.abs(score - classAvg.doubleValue())) + "分，表现不错。");
            } else if (score < classAvg.doubleValue()) {
                suggestion.setSummary(suggestion.getSummary() + " 你的成绩低于班级平均分" + 
                        String.format("%.1f", Math.abs(score - classAvg.doubleValue())) + "分，需要加油。");
            }
        }
        
        return suggestion;
    }
    
    private Map<String, Object> generateStudentOverallAnalysis(Student student) {
        List<Submission> submissions = submissionRepository.findGradedByStudentId(student.getId());
        
        if (submissions.isEmpty()) {
            return createEmptyAnalysisResponse();
        }
        
        double avgScore = submissions.stream().mapToDouble(Submission::getScore).average().orElse(0);
        long aboveAvgCount = submissions.stream()
                .filter(s -> {
                    BigDecimal classAvg = s.getHomework().getAvgScore();
                    return classAvg != null && s.getScore() > classAvg.doubleValue();
                })
                .count();
        long onTimeCount = submissions.stream()
                .filter(s -> s.getSubmissionLateMinutes() == null || s.getSubmissionLateMinutes() == 0)
                .count();
        
        StringBuilder summary = new StringBuilder();
        summary.append("本学期共完成 ").append(submissions.size()).append(" 次作业，");
        summary.append("平均分 ").append(String.format("%.1f", avgScore)).append(" 分，");
        summary.append("按时提交率 ").append(String.format("%.1f", onTimeCount * 100.0 / submissions.size())).append("%，");
        summary.append("其中 ").append(aboveAvgCount).append(" 次作业高于班级平均水平。");
        
        if (avgScore >= 85) {
            summary.append("整体表现优秀，继续保持！");
        } else if (avgScore >= 70) {
            summary.append("整体表现良好，部分知识点可加强。");
        } else {
            summary.append("整体有待提升，建议加强课后练习。");
        }
        
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("1. 针对薄弱知识点进行专项练习\n");
        suggestions.append("2. 按时提交作业，避免迟交影响平时成绩\n");
        suggestions.append("3. 多参考优秀作业，学习解题思路\n");
        
        if (avgScore < 70) {
            suggestions.append("4. 建议每周至少复习3次，每次1小时\n");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary.toString());
        result.put("suggestions", suggestions.toString());
        result.put("totalHomework", submissions.size());
        result.put("avgScore", avgScore);
        result.put("onTimeRate", onTimeCount * 100.0 / submissions.size());
        
        return result;
    }
    
    private Map<String, Object> createEmptyAnalysisResponse() {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "暂无作业数据，无法生成分析报告");
        result.put("suggestions", "请先完成并提交作业");
        result.put("totalHomework", 0);
        result.put("avgScore", 0);
        return result;
    }
    
    // ==================== 辅助方法 ====================
    
    private StudentHomeworkDTO convertToStudentHomeworkDTO(Homework homework, Long studentId) {
        StudentHomeworkDTO dto = new StudentHomeworkDTO();
        dto.setId(homework.getId());
        dto.setName(homework.getName());
        dto.setCourseName(homework.getCourse().getName());
        dto.setCourseId(homework.getCourse().getId());
        dto.setTotalScore(homework.getTotalScore());
        dto.setQuestionCount(homework.getQuestionCount());
        dto.setStatus(homework.getStatus().toString());
        dto.setDeadline(homework.getDeadline());
        dto.setCreatedAt(homework.getCreatedAt());
        dto.setClassAvgScore(homework.getAvgScore());
        
        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(studentId, homework.getId());
        if (submission.isPresent()) {
            Submission sub = submission.get();
            dto.setMyScore(sub.getScore());
            dto.setSubmitStatus(sub.getStatus().toString());
            dto.setIsLate(sub.getSubmissionLateMinutes() != null && sub.getSubmissionLateMinutes() > 0);
        } else {
            dto.setMyScore(null);
            dto.setSubmitStatus("PENDING");
            dto.setIsLate(false);
        }
        return dto;
    }
    
    private MySubmissionInfo getMySubmissionInfo(Long studentId, Homework homework) {
        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(studentId, homework.getId());
        
        MySubmissionInfo info = new MySubmissionInfo();
        if (submission.isPresent()) {
            Submission sub = submission.get();
            info.setSubmissionId(sub.getId());
            info.setContent(sub.getContent());
            info.setAttachments(sub.getAttachments());
            info.setScore(sub.getScore());
            info.setFeedback(sub.getFeedback());
            info.setStatus(sub.getStatus().toString());
            info.setSubmittedAt(sub.getSubmittedAt());
            info.setGradedAt(sub.getGradedAt());
            info.setIsLate(sub.getSubmissionLateMinutes() != null && sub.getSubmissionLateMinutes() > 0);
            info.setLateMinutes(sub.getSubmissionLateMinutes());
            info.setAiFeedback(sub.getAiFeedback());
            info.setKnowledgePointScores(parseKnowledgePointScores(sub.getKnowledgePointScores()));
        } else {
            info.setStatus("PENDING");
            info.setIsLate(false);
        }
        return info;
    }
    
    private ClassStatisticsDTO getClassStatistics(Homework homework) {
        ClassStatisticsDTO stats = new ClassStatisticsDTO();
        
        long totalStudents = studentService.countByCourse(homework.getCourse());
        stats.setTotalStudents((int) totalStudents);
        
        List<Submission> gradedSubmissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        stats.setSubmittedCount(gradedSubmissions.size());
        
        if (!gradedSubmissions.isEmpty()) {
            double avg = gradedSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0);
            double max = gradedSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .max()
                    .orElse(0);
            double min = gradedSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .min()
                    .orElse(0);
            
            stats.setAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            stats.setHighestScore(BigDecimal.valueOf(max));
            stats.setLowestScore(BigDecimal.valueOf(min));
            
            long passCount = gradedSubmissions.stream()
                    .filter(s -> s.getScore() >= 60)
                    .count();
            long excellentCount = gradedSubmissions.stream()
                    .filter(s -> s.getScore() >= 80)
                    .count();
            stats.setPassRate(BigDecimal.valueOf(passCount * 100.0 / gradedSubmissions.size())
                    .setScale(2, RoundingMode.HALF_UP));
            stats.setExcellentRate(BigDecimal.valueOf(excellentCount * 100.0 / gradedSubmissions.size())
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            stats.setAvgScore(BigDecimal.ZERO);
            stats.setHighestScore(BigDecimal.ZERO);
            stats.setLowestScore(BigDecimal.ZERO);
            stats.setPassRate(BigDecimal.ZERO);
            stats.setExcellentRate(BigDecimal.ZERO);
        }
        return stats;
    }
    
    private List<KnowledgePointMasteryDTO> getKnowledgePointAnalysis(Long studentId, Homework homework) {
        List<KnowledgePointMasteryDTO> result = new ArrayList<>();
        
        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(studentId, homework.getId());
        if (!submission.isPresent() || submission.get().getKnowledgePointScores() == null) {
            return result;
        }
        
        Submission sub = submission.get();
        Map<String, Integer> myKpScores = parseKnowledgePointScores(sub.getKnowledgePointScores());
        Map<String, BigDecimal> classAvgRates = calculateClassAvgRatesForHomework(homework);
        
        for (Map.Entry<String, Integer> entry : myKpScores.entrySet()) {
            String kpId = entry.getKey();
            Integer myScore = entry.getValue();
            
            KnowledgePointMasteryDTO dto = new KnowledgePointMasteryDTO();
            dto.setKnowledgePointId(Long.parseLong(kpId));
            dto.setKnowledgePointName(getKnowledgePointName(Long.parseLong(kpId)));
            dto.setMyScore(myScore);
            dto.setFullScore(10);
            dto.setScoreRate(BigDecimal.valueOf(myScore * 10.0).setScale(2, RoundingMode.HALF_UP));
            
            BigDecimal classAvg = classAvgRates.getOrDefault(kpId, BigDecimal.ZERO);
            dto.setClassAvgRate(classAvg);
            
            double myRate = myScore * 10.0;
            if (myRate >= 80) {
                dto.setLevel("GOOD");
                dto.setSuggestion("✅ 掌握良好，继续保持");
            } else if (myRate >= 60) {
                dto.setLevel("MODERATE");
                dto.setSuggestion("📚 基本掌握，建议加强练习");
            } else {
                dto.setLevel("WEAK");
                dto.setSuggestion("🔴 薄弱知识点，需要重点复习");
            }
            result.add(dto);
        }
        
        result.sort(Comparator.comparing(KnowledgePointMasteryDTO::getScoreRate));
        return result;
    }
    
    private ScoreAnalysisDTO getScoreAnalysis(Long studentId, Homework homework) {
        ScoreAnalysisDTO analysis = new ScoreAnalysisDTO();
        
        Optional<Submission> submission = submissionRepository.findByStudentIdAndHomeworkId(studentId, homework.getId());
        
        if (!submission.isPresent() || submission.get().getScore() == null) {
            analysis.setScore(BigDecimal.valueOf(-1));
            analysis.setClassAvg(homework.getAvgScore() != null ? homework.getAvgScore() : BigDecimal.ZERO);
            analysis.setDiffFromAvg(BigDecimal.ZERO);
            analysis.setTrend("UNKNOWN");
            analysis.setRank(null);
            return analysis;
        }
        
        Submission sub = submission.get();
        BigDecimal myScore = BigDecimal.valueOf(sub.getScore());
        BigDecimal classAvg = homework.getAvgScore() != null ? homework.getAvgScore() : BigDecimal.ZERO;
        
        analysis.setScore(myScore);
        analysis.setClassAvg(classAvg);
        analysis.setDiffFromAvg(myScore.subtract(classAvg).setScale(2, RoundingMode.HALF_UP));
        
        List<Submission> gradedSubmissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        long higherScoreCount = gradedSubmissions.stream()
                .filter(s -> s.getScore() != null && sub.getScore() != null)
                .filter(s -> s.getScore() > sub.getScore())
                .count();
        int rank = (int) higherScoreCount + 1;
        analysis.setRank(rank);
        analysis.setTrend(getScoreTrend(studentId, homework));
        
        ScoreDistributionDTO distribution = new ScoreDistributionDTO();
        distribution.setExcellentCount(0);
        distribution.setGoodCount(0);
        distribution.setMediumCount(0);
        distribution.setPassCount(0);
        distribution.setFailCount(0);
        
        for (Submission s : gradedSubmissions) {
            Double score = s.getScore();
            if (score == null) continue;
            
            if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
            else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
            else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
            else distribution.setFailCount(distribution.getFailCount() + 1);
        }
        analysis.setDistribution(distribution);
        
        return analysis;
    }
    
    private String getScoreTrend(Long studentId, Homework currentHomework) {
        List<Homework> homeworks = homeworkRepository.findByCourseIdOrderByDeadlineDesc(currentHomework.getCourse().getId());
        
        Submission currentSub = submissionRepository.findByStudentIdAndHomeworkId(studentId, currentHomework.getId()).orElse(null);
        if (currentSub == null || currentSub.getScore() == null) {
            return "UNKNOWN";
        }
        
        for (Homework hw : homeworks) {
            if (hw.getId().equals(currentHomework.getId())) continue;
            Optional<Submission> prevSub = submissionRepository.findByStudentIdAndHomeworkId(studentId, hw.getId());
            if (prevSub.isPresent() && prevSub.get().getScore() != null) {
                Double diff = currentSub.getScore() - prevSub.get().getScore();
                if (diff >= 5) return "IMPROVING";
                else if (diff <= -5) return "DECLINING";
                else return "STABLE";
            }
        }
        return "STABLE";
    }
    
    private Map<String, BigDecimal> calculateClassAvgRatesForHomework(Homework homework) {
        Map<String, BigDecimal> avgRates = new HashMap<>();
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        
        if (submissions.isEmpty()) return avgRates;
        
        Map<String, List<Integer>> kpScores = new HashMap<>();
        
        for (Submission sub : submissions) {
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
    
    private String getKnowledgePointName(Long kpId) {
        Optional<KnowledgePoint> knowledgePoint = knowledgePointRepository.findById(kpId);
        if (knowledgePoint.isPresent()) {
            return knowledgePoint.get().getName();
        }
        return "知识点" + kpId;
    }
    
    private Semester getCurrentSemester() {
        List<Semester> semesters = semesterService.findAll();
        return semesters.stream()
                .filter(Semester::getIsCurrent)
                .findFirst()
                .orElse(null);
    }
}