package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.edu.domain.AiAnalysisReport;
import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;
import com.edu.domain.Homework;
import com.edu.domain.Semester;
import com.edu.domain.Submission;
import com.edu.domain.dto.HomeworkAiAnalysisDTO;
import com.edu.domain.dto.HomeworkAnalysisDTO;
import com.edu.domain.dto.HomeworkDetailDTO;
import com.edu.domain.dto.KnowledgePointErrorDTO;
import com.edu.domain.dto.ScoreDistributionDTO;
import com.edu.repository.ExamRepository;
import com.edu.repository.HomeworkRepository;
import com.edu.repository.SubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkAnalysisService {
   private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseService courseService;
    private final SemesterService semesterService;
    private final StudentService studentService;
    private final AiAnalysisReportService aiReportService;
    private final ClassWrongQuestionStatsService classWrongQuestionStatsService;

    /**
     * 获取当前学期的作业列表（分页+筛选）
     */
    public Page<HomeworkAnalysisDTO> getHomeworkList(String status, String keyword, Long courseId, int page, int size) {
        // 获取当前学期
        Semester currentSemester = semesterService.findAll().stream()
                .filter(Semester::getIsCurrent)
                .findFirst()
                .orElse(null);
        
        if (currentSemester == null) {
            return Page.empty();
        }
        
        // 获取当前学期下的课程ID列表
        List<Long> courseIds;
        if (courseId != null && courseId > 0) {
            courseIds = Collections.singletonList(courseId);
        } else {
            courseIds = courseService.findAll().stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Homework> homeworkPage = homeworkRepository.findWithFilters(courseIds, status, keyword, pageable);
        
        return homeworkPage.map(this::convertToDTO);
    }
    
    /**
     * 获取作业详情（包含分析数据）
     */
    @Transactional
    public HomeworkDetailDTO getHomeworkDetail(Long homeworkId) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        HomeworkDetailDTO detail = new HomeworkDetailDTO();
        // 基础信息
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
        detail.setAvgScore(homework.getAvgScore());
        detail.setPassRate(homework.getPassRate());
        
        // 统计提交情况
        int totalStudents = getTotalStudentsByCourseId(homework.getCourse().getId());
        int submittedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(homeworkId, "PENDING");
        int onTimeCount = (int) submissionRepository.countOnTimeByHomeworkId(homeworkId);
        
        detail.setTotalStudents(totalStudents);
        detail.setSubmissionCount(submittedCount);
        detail.setSubmitRate(totalStudents > 0 ? 
                BigDecimal.valueOf(submittedCount * 100.0 / totalStudents).setScale(2, RoundingMode.HALF_UP).toString() : BigDecimal.ZERO.toString());
        
        // 成绩分布
        detail.setScoreDistribution(getScoreDistribution(homeworkId));
        
        // 知识点错题分析
        detail.setKnowledgePointErrors(getKnowledgePointErrors(homeworkId, totalStudents));
        
        // AI分析（优先从数据库获取，如果没有则调用AI生成并存储）
        HomeworkAiAnalysisDTO aiAnalysis = getOrCreateAiAnalysis(homework);
        detail.setAiAnalysis(aiAnalysis);
        
        return detail;
    }
    
    /**
     * 获取作业统计卡片数据（4个指标）
     */
    public Map<String, Object> getStatisticsCards() {
        Map<String, Object> cards = new HashMap<>();
        
        Semester currentSemester = getCurrentSemester();
        if (currentSemester == null) {
            return emptyCards();
        }
        
        List<Long> courseIds = getCurrentSemesterCourseIds();
        
        // 1. 作业总数
        long totalHomework = homeworkRepository.countByCourseIdIn(courseIds);
        cards.put("totalHomework", totalHomework);
        
        // 2. 作业平均分
        BigDecimal avgScore = homeworkRepository.getAvgScoreAll(courseIds);
        cards.put("avgScore", avgScore != null ? avgScore.setScale(2, RoundingMode.HALF_UP) : 0);
        
        // 3. 作业平均及格率
        BigDecimal avgPassRate = homeworkRepository.getAvgPassRateAll(courseIds);
        cards.put("avgPassRate", avgPassRate != null ? avgPassRate.setScale(2, RoundingMode.HALF_UP) : 0);
        
        // 4. 作业按时提交率
        BigDecimal onTimeRate = calculateOverallOnTimeRate(courseIds);
        cards.put("onTimeRate", onTimeRate);
        
        return cards;
    }
    
    /**
     * 获取作业趋势图数据
     */
    public Map<String, Object> getTrendData() {
        Map<String, Object> trendData = new HashMap<>();
        
        Semester currentSemester = getCurrentSemester();
        if (currentSemester == null) {
            return trendData;
        }
        
        List<Long> courseIds = getCurrentSemesterCourseIds();
        
        // 获取按时间排序的作业平均分
        List<Object[]> rawData = homeworkRepository.getScoreTrend(courseIds);
        
        List<String> dates = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();
        
        for (Object[] row : rawData) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            BigDecimal avgScore = (BigDecimal) row[1];
            if (avgScore != null) {
                dates.add(createdAt.toLocalDate().toString());
                scores.add(avgScore.setScale(2, RoundingMode.HALF_UP));
            }
        }
        
        trendData.put("dates", dates);
        trendData.put("scores", scores);
        
        // 计算趋势（上升/下降/稳定）
        if (scores.size() >= 2) {
            BigDecimal first = scores.get(0);
            BigDecimal last = scores.get(scores.size() - 1);
            int compare = last.compareTo(first);
            if (compare > 0) {
                trendData.put("trend", "上升");
                trendData.put("trendValue", last.subtract(first).setScale(2, RoundingMode.HALF_UP));
            } else if (compare < 0) {
                trendData.put("trend", "下降");
                trendData.put("trendValue", first.subtract(last).setScale(2, RoundingMode.HALF_UP));
            } else {
                trendData.put("trend", "稳定");
                trendData.put("trendValue", 0);
            }
        } else {
            trendData.put("trend", "数据不足");
            trendData.put("trendValue", 0);
        }
        
        return trendData;
    }
    
    /**
     * 获取作业整体AI分析报告
     */
    public Map<String, Object> getOverallAnalysis() {
        Semester currentSemester = getCurrentSemester();
        if (currentSemester == null) {
           Map<String, Object> analysisData = new HashMap<>();
             analysisData.put(  "error", "无当前学期数据");
            return analysisData;
        }
        
        // 尝试从数据库获取已有的分析报告
        AiAnalysisReport existingReport = aiReportService.findLatestReport("OVERALL", 1L, "HOMEWORK");
        
        if (existingReport != null && existingReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            // 7天内的报告直接返回
            
             Map<String, Object> analysisData = new HashMap<>();
            analysisData.put(  "summary", existingReport.getSummary());
            analysisData.put(  "suggestions", existingReport.getSuggestions());
            analysisData.put(  "analysisData", existingReport.getAnalysisData());
            analysisData.put(  "createdAt", existingReport.getCreatedAt());
            analysisData.put(  "fromCache", true);

            return analysisData;
        }
        
        // 生成新的分析报告（模拟AI分析）
        Map<String, Object> overallAnalysis = generateOverallAnalysis();
        
        // 存储到数据库
        AiAnalysisReport report = new AiAnalysisReport();
        report.setTargetType("OVERALL");
        report.setTargetId(1L);
        report.setSemester(getCurrentSemester());
        report.setReportType("HOMEWORK");
       // 方式二：用 ObjectMapper 转成 JSON 字符串
        ObjectMapper mapper = new ObjectMapper();
       try {
          Map<String, Object> analysisDataMap = new HashMap<>();
          analysisDataMap.put("avgScore", 85.5);
          analysisDataMap.put("weakPoints", Arrays.asList("Redis", "MySQL"));
          String analysisDataJson = mapper.writeValueAsString(analysisDataMap);
          report.setAnalysisData(analysisDataJson);
          } catch (JsonProcessingException e) {
              e.printStackTrace();
              report.setAnalysisData("{}");  // 失败时给空对象
          }
        report.setSummary((String) overallAnalysis.get("summary"));
        report.setSuggestions((String) overallAnalysis.get("suggestions"));
        report.setCreatedAt(LocalDateTime.now());
        aiReportService.save(report);
        
        overallAnalysis.put("fromCache", false);
        return overallAnalysis;
    }
    
    // ==================== 私有辅助方法 ====================
    
    private Semester getCurrentSemester() {
        return semesterService.findAll().stream()
                .filter(Semester::getIsCurrent)
                .findFirst()
                .orElse(null);
    }
    
    private List<Long> getCurrentSemesterCourseIds() {
        return courseService.findAll().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
    }
    
    private int getTotalStudentsByCourseId(Long courseId) {
        // 获取选修该课程的学生数量
        return (int) studentService.findByCourseId(courseId).size();
    }
    
    private HomeworkAnalysisDTO convertToDTO(Homework homework) {
        HomeworkAnalysisDTO dto = new HomeworkAnalysisDTO();
        dto.setId(homework.getId());
        dto.setName(homework.getName());
        dto.setCourseName(homework.getCourse().getName());
        dto.setCourseId(homework.getCourse().getId());
        dto.setTotalScore(homework.getTotalScore());
        dto.setQuestionCount(homework.getQuestionCount());
        dto.setStatus(homework.getStatus().toString());
        dto.setDeadline(homework.getDeadline());
        dto.setCreatedAt(homework.getCreatedAt());
        dto.setAvgScore(homework.getAvgScore());
        dto.setPassRate(homework.getPassRate());
        return dto;
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
            return distribution;
        }
        
        for (Integer score : scores) {
            if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
            else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
            else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
            else distribution.setFailCount(distribution.getFailCount() + 1);
        }
        
        // 计算统计值
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        
        distribution.setAverageScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        distribution.setHighestScore(BigDecimal.valueOf(max));
        distribution.setLowestScore(BigDecimal.valueOf(min));
        
        return distribution;
    }
    
    private List<KnowledgePointErrorDTO> getKnowledgePointErrors(Long homeworkId, int totalStudents) {
        List<KnowledgePointErrorDTO> result = new ArrayList<>();
        
        // 从作业的 knowledge_points_mapping 获取知识点分布
        Homework homework = homeworkRepository.findById(homeworkId).orElse(null);
        if (homework == null || homework.getKnowledgePointsMapping() == null) {
            return result;
        }
        
        // 解析知识点映射，获取涉及的知识点ID
        // 这里简化处理，实际需要解析JSON
        Set<Long> kpIds = extractKnowledgePointIds(homework);
        
        for (Long kpId : kpIds) {
            // 统计该知识点错误人数
            int errorCount = countErrorsForKnowledgePoint(homework, kpId);
            
            if (errorCount > 0) {
                KnowledgePointErrorDTO error = new KnowledgePointErrorDTO();
                error.setKnowledgePointId(kpId);
                error.setKnowledgePointName(getKnowledgePointName(kpId));
                error.setErrorCount(errorCount);
                error.setTotalStudents(totalStudents);
                error.setErrorRate(BigDecimal.valueOf(errorCount * 100.0 / totalStudents)
                        .setScale(2, RoundingMode.HALF_UP));
                error.setSuggestion(generateSuggestionForKnowledgePoint(error.getErrorRate()));
                result.add(error);
            }
        }
        
        // 按错误率排序
        result.sort((a, b) -> b.getErrorRate().compareTo(a.getErrorRate()));
        
        return result;
    }
    
    private Set<Long> extractKnowledgePointIds(Homework homework) {
        Set<Long> kpIds = new HashSet<>();
        // 解析 JSON 字段 knowledge_points_mapping
        // 格式: {"1": [1,2], "2": [2,3]} 题目ID到知识点ID列表的映射
        try {
            String mapping = homework.getKnowledgePointsMapping();
            if (mapping != null && !mapping.isEmpty()) {
                // 简化处理：从字符串中提取数字
                // 实际应使用 Jackson 解析
                String[] parts = mapping.split("[\\[\\],]");
                for (String part : parts) {
                    if (part.matches("\\d+")) {
                        kpIds.add(Long.parseLong(part));
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析知识点映射失败", e);
        }
        return kpIds;
    }
    
    private int countErrorsForKnowledgePoint(Homework homework, Long knowledgePointId) {
        // 从 submission 的 knowledge_point_scores 中统计得分低于60%的人数
        // 简化：假设得分<6（满分10分制）为错误
        // 实际需要更精确的计算
        List<Submission> submissions = submissionRepository.findByHomework(homework);
        int errorCount = 0;
        for (Submission sub : submissions) {
            if (sub.getScore() != null && sub.getScore() < 60) {
                errorCount++;
            }
        }
        return errorCount;
    }
    
    private String getKnowledgePointName(Long kpId) {
        // 从数据库获取知识点名称
        return "知识点" + kpId; // 简化
    }
    
    private String generateSuggestionForKnowledgePoint(BigDecimal errorRate) {
        if (errorRate.doubleValue() >= 70) {
            return "🔴 严重薄弱点，建议安排专项复习课";
        } else if (errorRate.doubleValue() >= 50) {
            return "🟡 薄弱点，需要加强练习";
        } else if (errorRate.doubleValue() >= 30) {
            return "🟢 基本掌握，可适当巩固";
        }
        return "✅ 掌握良好，继续保持";
    }
    
    private BigDecimal calculateOverallOnTimeRate(List<Long> courseIds) {
        // 计算所有作业的平均按时提交率
        List<Homework> homeworks = homeworkRepository.findWithFilters(courseIds, null, null, Pageable.unpaged()).getContent();
        if (homeworks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        double totalRate = 0;
        for (Homework hw : homeworks) {
            int totalStudents = getTotalStudentsByCourseId(hw.getCourse().getId());
            if (totalStudents > 0) {
                int onTimeCount = (int) submissionRepository.countOnTimeByHomeworkId(hw.getId());
                totalRate += onTimeCount * 100.0 / totalStudents;
            }
        }
        
        double avgRate = totalRate / homeworks.size();
        return BigDecimal.valueOf(avgRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private Map<String, Object> emptyCards() {
      Map<String, Object> statusCardData = new HashMap<>();
        statusCardData.put( "totalHomework", 0);
        statusCardData.put(  "avgScore", 0);
        statusCardData.put(    "avgPassRate", 0);
        statusCardData.put(   "onTimeRate", 0);
        return statusCardData;
    }
    
    private HomeworkAiAnalysisDTO getOrCreateAiAnalysis(Homework homework) {
        // 优先从 homework 表的扩展字段获取
        if (homework.getAiParsedData() != null) {
            // 已有AI数据，解析返回
            return parseAiData(homework);
        }
        
        // 调用AI生成分析（模拟）
        HomeworkAiAnalysisDTO analysis = generateAiAnalysisForHomework(homework);
        
        // 存储到数据库
        homework.setAiParsedData(convertToJsonNode(analysis));
        homeworkRepository.save(homework);
        
        return analysis;
    }
    
    private HomeworkAiAnalysisDTO generateAiAnalysisForHomework(Homework homework) {
        HomeworkAiAnalysisDTO analysis = new HomeworkAiAnalysisDTO();
        
        // 模拟AI分析逻辑
        if (homework.getAvgScore() != null) {
            double avgScore = homework.getAvgScore().doubleValue();
            if (avgScore >= 85) {
                analysis.setSummary("本次作业整体表现优秀，大部分学生掌握良好");
                analysis.setStrengths(Arrays.asList("基础知识扎实", "解题思路清晰", "代码规范"));
                analysis.setWeaknesses(Arrays.asList("部分难题得分率偏低", "综合应用题有待提高"));
            } else if (avgScore >= 70) {
                analysis.setSummary("本次作业整体表现良好，部分知识点需要加强");
                analysis.setStrengths(Arrays.asList("基础题完成度高", "提交及时"));
                analysis.setWeaknesses(Arrays.asList("中等难度题错误较多", "细节处理不到位"));
            } else {
                analysis.setSummary("本次作业整体表现有待提升，建议针对性复习");
                analysis.setStrengths(Arrays.asList("参与度高", "部分学生进步明显"));
                analysis.setWeaknesses(Arrays.asList("基础概念模糊", "解题步骤不完整"));
            }
        } else {
            analysis.setSummary("作业正在进行中，暂未生成完整分析");
            analysis.setStrengths(Arrays.asList("待批改完成后查看"));
            analysis.setWeaknesses(Arrays.asList("待批改完成后查看"));
        }
        
        analysis.setSuggestions(Arrays.asList(
            "建议针对错题进行专项练习",
            "可参考优秀作业学习解题思路",
            "及时复习相关知识点"
        ));
        
        analysis.setDetailedReport("详细分析报告待AI生成...");
        
        return analysis;
    }
    
    private HomeworkAiAnalysisDTO parseAiData(Homework homework) {
        // 解析 JSON 数据
        HomeworkAiAnalysisDTO analysis = new HomeworkAiAnalysisDTO();
        analysis.setSummary("已分析完成");
        analysis.setStrengths(Arrays.asList("良好"));
        analysis.setWeaknesses(Arrays.asList("待改进"));
        analysis.setSuggestions(Arrays.asList("继续努力"));
        return analysis;
    }
    
   private String convertToJsonNode(HomeworkAiAnalysisDTO analysis) {
    try {
        ObjectMapper mapper = new ObjectMapper();
        // 直接转成 JSON 字符串
        return mapper.writeValueAsString(analysis);
    } catch (Exception e) {
        log.error("转换JSON失败", e);
        return "{}";
    }
}
    
    private Map<String, Object> generateOverallAnalysis() {
        // 获取统计数据
        Map<String, Object> cards = getStatisticsCards();
        Map<String, Object> trend = getTrendData();
        
        long totalHomework = (long) cards.get("totalHomework");
        BigDecimal avgScore = (BigDecimal) cards.get("avgScore");
        BigDecimal avgPassRate = (BigDecimal) cards.get("avgPassRate");
        BigDecimal onTimeRate = (BigDecimal) cards.get("onTimeRate");
        
        StringBuilder summary = new StringBuilder();
        summary.append("本学期共布置 ").append(totalHomework).append(" 次作业，");
        summary.append("班级平均分 ").append(avgScore).append(" 分，");
        summary.append("平均及格率 ").append(avgPassRate).append("%，");
        summary.append("按时提交率 ").append(onTimeRate).append("%。");
        
        if (avgScore.doubleValue() >= 80) {
            summary.append("整体作业完成质量优秀，学生掌握情况良好。");
        } else if (avgScore.doubleValue() >= 70) {
            summary.append("整体作业完成质量良好，部分知识点需要加强。");
        } else {
            summary.append("整体作业完成质量有待提升，建议加强教学辅导。");
        }
        
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("1. 针对薄弱知识点增加专项练习\n");
        suggestions.append("2. 鼓励学生按时提交作业，提高完成率\n");
        suggestions.append("3. 定期组织作业讲评，帮助学生查漏补缺\n");
        
        if (onTimeRate.doubleValue() < 80) {
            suggestions.append("4. 作业截止时间设置可适当调整，提高提交率\n");
        }

         Map<String, Object> aiSuggestionMap = new HashMap<>();
        aiSuggestionMap.put("summary", summary.toString());
        aiSuggestionMap.put( "suggestions", suggestions.toString());
        aiSuggestionMap.put(  "statistics", cards);
        aiSuggestionMap.put( "trend", trend);

        
        return aiSuggestionMap;
    }
}
