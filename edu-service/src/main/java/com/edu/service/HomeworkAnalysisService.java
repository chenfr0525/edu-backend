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
import com.edu.domain.SubmissionStatus;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.HomeworkAiAnalysisDTO;
import com.edu.domain.dto.HomeworkAnalysisDTO;
import com.edu.domain.dto.HomeworkDetailDTO;
import com.edu.domain.dto.KnowledgePointErrorDTO;
import com.edu.domain.dto.ScoreDistributionDTO;
import com.edu.repository.ExamRepository;
import com.edu.repository.HomeworkRepository;
import com.edu.repository.KnowledgePointRepository;
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
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentService studentService;
    private final AiAnalysisReportService aiReportService;
    private final ObjectMapper objectMapper;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;    

    /**
     * 获取当前学期的作业列表（分页+筛选）
     */
    public Page<HomeworkAnalysisDTO> getHomeworkList(String status, String keyword, Long courseId, int page, int size) {        
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
        Page<Homework> homeworkPage = homeworkRepository.findWithFilters(courseIds, keyword, pageable);
        
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
        int submittedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(homeworkId, SubmissionStatus.PENDING);
        
        detail.setTotalStudents(totalStudents);
        detail.setSubmissionCount(submittedCount);
        detail.setSubmitRate(totalStudents > 0 ? 
                BigDecimal.valueOf(submittedCount * 100.0 / totalStudents).setScale(2, RoundingMode.HALF_UP).toString() : BigDecimal.ZERO.toString());
        
        // 成绩分布
        detail.setScoreDistribution(getScoreDistribution(homeworkId));
        
        // 知识点错题分析（从 homework.knowledgePointIds 获取）
        detail.setKnowledgePointErrors(getKnowledgePointErrors(homework, totalStudents));
        
        // AI分析（从 ai_analysis_report 表获取）
        HomeworkAiAnalysisDTO aiAnalysis = getAiAnalysisFromReport(homework);
        detail.setAiAnalysis(aiAnalysis);
        
        return detail;
    }
    
    /**
     * 获取作业统计卡片数据（4个指标）
     */
    public Map<String, Object> getStatisticsCards() {
        Map<String, Object> cards = new HashMap<>();
        
        List<Long> courseIds = courseService.findAll().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        
        if (courseIds.isEmpty()) {
            return emptyCards();
        }
        
        // 1. 作业总数
        long totalHomework = homeworkRepository.countByCourseIdIn(courseIds);
        cards.put("totalHomework", totalHomework);
        
        // 2. 作业平均分
        BigDecimal avgScore = homeworkRepository.getAvgScoreAll(courseIds);
        cards.put("avgScore", avgScore != null ? avgScore.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
         
        // 3. 作业平均及格率
        BigDecimal avgPassRate = homeworkRepository.getAvgPassRateAll(courseIds);
        cards.put("avgPassRate", avgPassRate != null ? avgPassRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        // 4. 作业按时提交率（简化：计算已批改比例）
        BigDecimal onTimeRate = calculateOverallSubmitRate(courseIds);
        cards.put("onTimeRate", onTimeRate);
        
        return cards;
    }
    
    /**
     * 获取作业趋势图数据
     */
    public Map<String, Object> getTrendData() {
        Map<String, Object> trendData = new HashMap<>();
        
        List<Long> courseIds = courseService.findAll().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        
        if (courseIds.isEmpty()) {
            return trendData;
        }
        
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
 * 获取作业整体AI分析报告（迁移到统一服务）
 */
public Map<String, Object> getOverallAnalysis() {
    List<Long> courseIds = courseService.findAll().stream()
            .map(Course::getId)
            .collect(Collectors.toList());
    
    if (courseIds.isEmpty()) {
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("error", "暂无课程数据");
        return analysisData;
    }
        
       AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
        "COURSE",
        courseIds.get(0),
        "HOMEWORK_OVERALL",
        false
    );
    
    // 转换为前端需要的格式
    Map<String, Object> result = new HashMap<>();
    result.put("summary", suggestion.getSummary());
    result.put("suggestions", String.join("\n", suggestion.getSuggestions()));
    result.put("analysisData", suggestion);
    result.put("createdAt", LocalDateTime.now());
    result.put("fromCache", false);
    
    return result;
}
    
    // ==================== 私有辅助方法 ====================
    
    private int getTotalStudentsByCourseId(Long courseId) {
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
            distribution.setAverageScore(BigDecimal.ZERO);
            distribution.setHighestScore(BigDecimal.ZERO);
            distribution.setLowestScore(BigDecimal.ZERO);
            return distribution;
        }
        
      for (Integer score : scores) {
            if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
            else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
            else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
            else distribution.setFailCount(distribution.getFailCount() + 1);
        }
        
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        
        distribution.setAverageScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        distribution.setHighestScore(BigDecimal.valueOf(max));
        distribution.setLowestScore(BigDecimal.valueOf(min));
        
         return distribution;
    }
    
    private List<KnowledgePointErrorDTO> getKnowledgePointErrors(Homework homework, int totalStudents) {
        List<KnowledgePointErrorDTO> result = new ArrayList<>();
        
        // 从 homework.knowledgePointIds 获取知识点ID列表
        List<Long> knowledgePointIds = homework.getKnowledgePointIds();
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            log.warn("作业 {} 未关联知识点", homework.getId());
            return result;
        }
        
        // 获取知识点名称
        Map<Long, String> kpNameMap = new HashMap<>();
        for (Long kpId : knowledgePointIds) {
            knowledgePointRepository.findById(kpId).ifPresent(kp -> 
                kpNameMap.put(kpId, kp.getName()));
        }
         // 统计每个知识点的错误人数（得分<60的学生）
        List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
        
        for (Long kpId : knowledgePointIds) {
            // 简化：统计作业得分<60的学生数作为该知识点的错误人数
            // 实际应根据题目-知识点映射精确计算
            int errorCount = 0;
            for (Submission sub : submissions) {
                if (sub.getScore() != null && sub.getScore() < 60) {
                    errorCount++;
                }
            }
         if (errorCount > 0) {
                KnowledgePointErrorDTO error = new KnowledgePointErrorDTO();
                error.setKnowledgePointId(kpId);
                error.setKnowledgePointName(kpNameMap.getOrDefault(kpId, "知识点-" + kpId));
                error.setErrorCount(errorCount);
                error.setTotalStudents(totalStudents);
                error.setErrorRate(BigDecimal.valueOf(errorCount * 100.0 / Math.max(totalStudents, 1))
                        .setScale(2, RoundingMode.HALF_UP));
                error.setSuggestion(generateSuggestionForKnowledgePoint(error.getErrorRate()));
                result.add(error);
            }
        }
        
        // 按错误率排序
        result.sort((a, b) -> b.getErrorRate().compareTo(a.getErrorRate()));
        
        return result;
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

     /**
     * 计算整体提交率
     */
    private BigDecimal calculateOverallSubmitRate(List<Long> courseIds) {
        List<Homework> homeworks = homeworkRepository.findWithFilters(courseIds, null, Pageable.unpaged()).getContent();
        if (homeworks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        double totalRate = 0;
        for (Homework hw : homeworks) {
            int totalStudents = getTotalStudentsByCourseId(hw.getCourse().getId());
            if (totalStudents > 0) {
                int submittedCount = (int) submissionRepository.countByHomeworkIdAndStatusNot(hw.getId(), SubmissionStatus.PENDING);
                totalRate += submittedCount * 100.0 / totalStudents;
            }
        }
        
        double avgRate = totalRate / homeworks.size();
        return BigDecimal.valueOf(avgRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
 * 从统一服务获取AI分析
 */
private HomeworkAiAnalysisDTO getAiAnalysisFromReport(Homework homework) {
    try {
        // 使用统一AI服务
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
            "HOMEWORK",              // targetType
            homework.getId(),        // targetId
            "HOMEWORK_ANALYSIS",     // reportType
            false
        );
        
        if (suggestion == null) {
            return null;
        }
            return HomeworkAiAnalysisDTO.builder()
            .summary(suggestion.getSummary())
            .strengths(suggestion.getStrengths())
            .weaknesses(suggestion.getWeaknesses())
            .suggestions(suggestion.getSuggestions())
            .detailedReport(suggestion.getSummary())
            .build();
    } catch (Exception e) {
        log.error("获取AI分析报告失败", e);
        return null;
    }
}

     private Map<String, Object> emptyCards() {
        Map<String, Object> statusCardData = new HashMap<>();
        statusCardData.put("totalHomework", 0);
        statusCardData.put("avgScore", BigDecimal.ZERO);
        statusCardData.put("avgPassRate", BigDecimal.ZERO);
        statusCardData.put("onTimeRate", BigDecimal.ZERO);
        return statusCardData;
    }
    
    private Map<String, Object> generateOverallAnalysis() {
        Map<String, Object> cards = getStatisticsCards();
        Map<String, Object> trend = getTrendData();
        
        long totalHomework = (long) cards.getOrDefault("totalHomework", 0L);
        BigDecimal avgScore = (BigDecimal) cards.getOrDefault("avgScore", BigDecimal.ZERO);
        BigDecimal avgPassRate = (BigDecimal) cards.getOrDefault("avgPassRate", BigDecimal.ZERO);
        BigDecimal onTimeRate = (BigDecimal) cards.getOrDefault("onTimeRate", BigDecimal.ZERO);
        
        StringBuilder summary = new StringBuilder();
        summary.append("共布置 ").append(totalHomework).append(" 次作业，");
        summary.append("平均分 ").append(avgScore).append(" 分，");
        summary.append("平均及格率 ").append(avgPassRate).append("%，");
        summary.append("提交率 ").append(onTimeRate).append("%。");
        
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
        aiSuggestionMap.put("suggestions", suggestions.toString());
        aiSuggestionMap.put("statistics", cards);
        aiSuggestionMap.put("trend", trend);
        
        return aiSuggestionMap;
    }
    
   
}
