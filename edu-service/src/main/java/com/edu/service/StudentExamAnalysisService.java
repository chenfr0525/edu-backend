package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.edu.common.PageResult;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.ExamClassStatisticsDTO;
import com.edu.domain.dto.ExamHistoryScoreDTO;
import com.edu.domain.dto.ExamKnowledgePointDTO;
import com.edu.domain.dto.ExamScoreAnalysisDTO;
import com.edu.domain.dto.ExamStatisticsCards;
import com.edu.domain.dto.ExamTrendData;
import com.edu.domain.dto.MyExamGradeInfoDTO;
import com.edu.domain.dto.ScoreDistributionDTO;
import com.edu.domain.dto.StudentExamDTO;
import com.edu.domain.dto.StudentExamDetailDTO;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.KnowledgePointRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentExamAnalysisService {
   private final ExamRepository examRepository;
    private final ExamGradeRepository examGradeRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

     /**
     * 获取或创建考试AI建议（迁移到统一服务）
     */
    private AiSuggestionDTO getOrCreateExamAiSuggestionV2(Student student, Exam exam) {
        // 委托给统一AI服务
        // 使用 "STUDENT" 作为 targetType
        // 使用 "EXAM_ANALYSIS_" + exam.getId() 作为 reportType 来区分不同考试
        String reportType = "EXAM_ANALYSIS_" + exam.getId();
        
        return unifiedAiAnalysisService.getOrCreateAnalysis(
            "STUDENT",
            student.getId(),
            reportType,
            false  // 不强制刷新
        );
    }

     // 刷新方法也要改
    public AiSuggestionDTO refreshExamAiAnalysis(Long studentId, Long examId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("考试不存在"));
        
        String reportType = "EXAM_ANALYSIS_" + exam.getId();
        
        // 强制刷新
        return unifiedAiAnalysisService.refreshAnalysis(
            "STUDENT",
            student.getId(),
            reportType
        );
    }

    /**
     * 1. 获取学生的考试列表（分页）- 保持不变
     */
    public PageResult<StudentExamDTO> getStudentExamListPage(
            Long studentId, Long courseId, String status, int pageNum, int pageSize) {
        // ... 保持不变 ...
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        List<Exam> allExams;
        if (courseId != null && courseId > 0) {
            allExams = examRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            allExams = examRepository.findByStudentId(studentId);
        }
        
        List<StudentExamDTO> allDTOs = allExams.stream()
                .map(exam -> convertToStudentExamDTO(exam, studentId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        int total = allDTOs.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
     
        List<StudentExamDTO> examList = allDTOs.subList(start, end);
        return new PageResult<>(examList, (long) total, pageNum, pageSize);
    }
    
    /**
     * 2. 获取学生单次考试的详细分析 - 保持不变
     */
    @Transactional
    public StudentExamDetailDTO getStudentExamDetail(Long studentId, Long examId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("考试不存在"));
        
        StudentExamDetailDTO detail = new StudentExamDetailDTO();
        
        detail.setId(exam.getId());
        detail.setName(exam.getName());
        detail.setType(exam.getType().toString());
        detail.setDescription(exam.getDescription());
        detail.setCourseName(exam.getCourse().getName());
        detail.setCourseId(exam.getCourse().getId());
        detail.setExamDate(exam.getExamDate());
        detail.setStartTime(exam.getStartTime() != null ? exam.getStartTime().toString() : null);
        detail.setEndTime(exam.getEndTime() != null ? exam.getEndTime().toString() : null);
        detail.setDuration(exam.getDuration());
        detail.setFullScore(exam.getFullScore());
        detail.setPassScore(exam.getPassScore());
        detail.setLocation(exam.getLocation());
        detail.setStatus(exam.getStatus().toString());
        
        detail.setMyGrade(getMyExamGradeInfo(studentId, exam));
        detail.setClassStats(getExamClassStatistics(exam));
        detail.setKnowledgePointAnalysis(getExamKnowledgePointAnalysis(studentId, exam));
        detail.setScoreAnalysis(getExamScoreAnalysis(studentId, exam));
        return detail;
    }
    
    /**
     * 3. 获取学生考试统计卡片 - 保持不变
     */
    public ExamStatisticsCards getStudentExamStatisticsCards(Long studentId) {
        // ... 保持不变 ...
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        ExamStatisticsCards cards = new ExamStatisticsCards();
        
        List<ExamGrade> examGrades = examGradeRepository.findAllByStudentIdOrderByDateAsc(studentId);
        
        if (examGrades.isEmpty()) {
            cards.setAvgScore(BigDecimal.ZERO);
            cards.setAvgRank(BigDecimal.ZERO);
            cards.setTotalExams(0);
            cards.setAboveAvgCount(0);
            cards.setAboveAvgRate(BigDecimal.ZERO);
            return cards;
        }
        
        double avgScore = examGrades.stream()
                .mapToInt(ExamGrade::getScore)
                .average()
                .orElse(0);
        cards.setAvgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        
        double avgRank = examGrades.stream()
                .filter(eg -> eg.getClassRank() != null)
                .mapToInt(ExamGrade::getClassRank)
                .average()
                .orElse(0);
        cards.setAvgRank(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
        
        cards.setTotalExams(examGrades.size());
        
        long aboveAvgCount = examGradeRepository.countAboveClassAvg(studentId);
        cards.setAboveAvgCount((int)aboveAvgCount);
        cards.setAboveAvgRate(BigDecimal.valueOf(aboveAvgCount * 100.0 / examGrades.size())
                .setScale(2, RoundingMode.HALF_UP));
        
        cards.setBestSubject("待计算");
        cards.setWeakestSubject("待计算");
        
        return cards;
    }
    
    /**
     * 4. 获取学生考试趋势图数据 - 保持不变
     */
    public ExamTrendData getStudentExamTrendData(Long studentId, Long courseId) {
        // ... 保持不变 ...
        ExamTrendData trendData = new ExamTrendData();
        
        List<ExamGrade> examGrades;
        String courseName = "全部课程";
        
        if (courseId != null && courseId > 0) {
            examGrades = examGradeRepository.findByStudentIdAndCourseIdOrderByDateAsc(studentId, courseId);
            Course course = courseService.findById(courseId).orElse(null);
            if (course != null) {
                courseName = course.getName();
            }
        } else {
            examGrades = examGradeRepository.findAllByStudentIdOrderByDateAsc(studentId);
        }
        
        List<String> examNames = new ArrayList<>();
        List<Integer> myScores = new ArrayList<>();
        List<BigDecimal> classAvgs = new ArrayList<>();
        List<Integer> myRanks = new ArrayList<>();
        
        for (ExamGrade eg : examGrades) {
            examNames.add(eg.getExam().getName());
            myScores.add(eg.getScore());
            
            BigDecimal classAvg = eg.getExam().getClassAvgScore();
            classAvgs.add(classAvg != null ? classAvg : BigDecimal.ZERO);
            myRanks.add(eg.getClassRank() != null ? eg.getClassRank() : 0);
        }
        
        trendData.setExamNames(examNames);
        trendData.setMyScores(myScores);
        trendData.setClassAvgs(classAvgs);
        trendData.setMyRanks(myRanks);
        trendData.setCourseName(courseName);
        
        if (myScores.size() >= 2) {
            Integer first = myScores.get(0);
            Integer last = myScores.get(myScores.size() - 1);
            if (last > first) {
                trendData.setTrend("上升");
                trendData.setTrendValue(last - first);
            } else if (last < first) {
                trendData.setTrend("下降");
                trendData.setTrendValue(first - last);
            } else {
                trendData.setTrend("稳定");
                trendData.setTrendValue(0);
            }
        } else {
            trendData.setTrend("数据不足");
            trendData.setTrendValue(0);
        }
        
        return trendData;
    }
   

    // ==================== 降级方案 ====================
    /**
     * 降级方案：当 AI 调用失败时使用
     */
    private AiSuggestionDTO getExamFallbackAiSuggestion(ExamGrade grade, BigDecimal classAvg) {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();
        int score = grade.getScore();
        
        if (score >= 90) {
            suggestion.setSummary("本次考试表现优秀！知识点掌握扎实，解题思路清晰。");
            suggestion.setStrengths(Arrays.asList("基础知识牢固", "解题规范", "时间分配合理"));
            suggestion.setWeaknesses(Arrays.asList("可挑战更高难度题目"));
            suggestion.setSuggestions(Arrays.asList("继续保持学习节奏", "尝试帮助其他同学", "预习下一阶段内容"));
        } else if (score >= 75) {
            suggestion.setSummary("本次考试表现良好，基础知识点掌握不错，部分细节需要完善。");
            suggestion.setStrengths(Arrays.asList("基础题正确率高", "考试态度认真"));
            suggestion.setWeaknesses(Arrays.asList("部分综合题扣分", "审题需更仔细"));
            suggestion.setSuggestions(Arrays.asList("复习错题相关知识点", "多做同类题型练习", "总结易错点"));
        } else if (score >= 60) {
            suggestion.setSummary("本次考试成绩及格，但仍有提升空间，建议加强薄弱环节。");
            suggestion.setStrengths(Arrays.asList("参与度高", "完成度良好"));
            suggestion.setWeaknesses(Arrays.asList("基础概念理解不深", "解题步骤不完整"));
            suggestion.setSuggestions(Arrays.asList("重新复习课堂笔记", "整理错题本", "向老师/同学请教"));
        } else {
            suggestion.setSummary("本次考试成绩不理想，需要重点关注基础知识的巩固。");
            suggestion.setStrengths(Arrays.asList("愿意参加考试"));
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
    
    // ==================== 原有的私有辅助方法（修复错误） ====================
    
    private StudentExamDTO convertToStudentExamDTO(Exam exam, Long studentId) {
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        
        if (!grade.isPresent()) {
            return null;
        } 
        
        StudentExamDTO dto = new StudentExamDTO();
        dto.setId(exam.getId());
        dto.setName(exam.getName());
        dto.setType(exam.getType().toString());
        dto.setCourseName(exam.getCourse().getName());
        dto.setCourseId(exam.getCourse().getId());
        dto.setExamDate(exam.getExamDate());
        dto.setFullScore(exam.getFullScore());
        dto.setStatus(exam.getStatus().toString());
        
        ExamGrade g = grade.get();
        dto.setMyScore(g.getScore());
        dto.setClassAvgScore(exam.getClassAvgScore());
        dto.setClassRank(g.getClassRank());
        dto.setScoreTrend(g.getScoreTrend());
        
        return dto;
    }
    
    private MyExamGradeInfoDTO getMyExamGradeInfo(Long studentId, Exam exam) {
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        
        MyExamGradeInfoDTO info = new MyExamGradeInfoDTO();
        if (grade.isPresent()) {
            ExamGrade g = grade.get();
            info.setGradeId(g.getId());
            info.setScore(g.getScore());
            info.setRemark(g.getRemark());
            info.setClassRank(g.getClassRank());
            info.setGradeRank(g.getGradeRank());
            info.setScoreTrend(g.getScoreTrend());
            info.setKnowledgePointScores(parseKnowledgePointScores(g.getKnowledgePointScores()));
            info.setCreatedAt(g.getCreatedAt());
        }
        return info;
    }
    
    private ExamClassStatisticsDTO getExamClassStatistics(Exam exam) {
        ExamClassStatisticsDTO stats = new ExamClassStatisticsDTO();
        
        List<Integer> scores = examGradeRepository.findScoresByExamId(exam.getId());
        
        if (scores.isEmpty()) {
            stats.setTotalStudents(0);
            stats.setAvgScore(BigDecimal.ZERO);
            stats.setHighestScore(BigDecimal.ZERO);
            stats.setLowestScore(BigDecimal.ZERO);
            stats.setPassRate(BigDecimal.ZERO);
            stats.setExcellentRate(BigDecimal.ZERO);
            return stats;
        }
        
        stats.setTotalStudents(scores.size());
        
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        
        stats.setAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        stats.setHighestScore(BigDecimal.valueOf(max));
        stats.setLowestScore(BigDecimal.valueOf(min));
        
        long passCount = scores.stream().filter(s -> s >= 60).count();
        long excellentCount = scores.stream().filter(s -> s >= 80).count();
        stats.setPassRate(BigDecimal.valueOf(passCount * 100.0 / scores.size())
                .setScale(2, RoundingMode.HALF_UP));
        stats.setExcellentRate(BigDecimal.valueOf(excellentCount * 100.0 / scores.size())
                .setScale(2, RoundingMode.HALF_UP));
        
        return stats;
    }
    
   private List<ExamKnowledgePointDTO> getExamKnowledgePointAnalysis(Long studentId, Exam exam) {
    List<ExamKnowledgePointDTO> result = new ArrayList<>();
    
    // 1. 参数校验
    if (studentId == null || exam == null) {
        log.warn("参数为空: studentId={}, exam={}", studentId, exam);
        return result;
    }
    
    // 2. 查询成绩
    Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
    if (!grade.isPresent()) {
        log.warn("未找到考试成绩: examId={}, studentId={}", exam.getId(), studentId);
        return result;
    }
    
    String knowledgePointScores = grade.get().getKnowledgePointScores();
    if (knowledgePointScores == null || knowledgePointScores.trim().isEmpty()) {
        log.warn("知识点分数为空: examId={}, studentId={}", exam.getId(), studentId);
        return result;
    }
    
    // 3. 解析知识点分数
    Map<String, Integer> myKpScores = parseKnowledgePointScores(knowledgePointScores);
    if (myKpScores == null || myKpScores.isEmpty()) {
        log.warn("解析知识点分数失败: {}", knowledgePointScores);
        return result;
    }
    
    // 4. 获取班级平均分
    Map<String, BigDecimal> classAvgRates = calculateExamClassAvgRates(exam);
    if (classAvgRates == null) {
        classAvgRates = new HashMap<>();
    }
    
    // 5. 遍历处理
    for (Map.Entry<String, Integer> entry : myKpScores.entrySet()) {
        String kpId = entry.getKey();
        Integer myScore = entry.getValue();
        
        // 跳过无效数据
        if (kpId == null || myScore == null) {
            continue;
        }
        
        try {
            Long kpIdLong = Long.parseLong(kpId);
            String kpName = getKnowledgePointName(kpIdLong);

            double myRate = myScore * 10.0; 
            
            BigDecimal classAvg = classAvgRates.getOrDefault(kpId, BigDecimal.ZERO);
            String level;
            String suggestion;
                if (myRate >= 80) {
                level="GOOD";
                suggestion="✅ 掌握良好，继续保持";
                } else if (myRate >= 60) {
                level="MODERATE";
                suggestion="📚 基本掌握，建议加强练习";
                } else {
                level="WEAK";
                suggestion="🔴 薄弱知识点，需要重点复习";
            }
        
            ExamKnowledgePointDTO dto = ExamKnowledgePointDTO.builder()
                .knowledgePointId(kpIdLong).knowledgePointName(kpName != null ? kpName : "知识点-" + kpId)
                .myScore(myScore)
                .fullScore(10)  // fullScore 是 Integer 类型，直接传 10
                .scoreRate(BigDecimal.valueOf(myRate).setScale(2, RoundingMode.HALF_UP))
                .classAvgRate(classAvg)  // classAvg 是 BigDecimal 类型
                .level(level)
                .suggestion(suggestion) // 根据得分率计算
                .build();
            
            result.add(dto);
        } catch (NumberFormatException e) {
            log.error("知识点ID解析失败: {}", kpId, e);
        }
    }
    
    // 6. 排序（处理 null 值）
    result.sort(Comparator.comparing(
        dto -> dto.getScoreRate() != null ? dto.getScoreRate() : BigDecimal.ZERO
    ));
    
    return result;
}


    
   private ExamScoreAnalysisDTO getExamScoreAnalysis(Long studentId, Exam exam) {
    ExamScoreAnalysisDTO analysis = new ExamScoreAnalysisDTO();
    
    Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
    if (!grade.isPresent()) {
        return analysis;
    }
    
    ExamGrade g = grade.get();
    analysis.setMyScore(g.getScore());
    analysis.setClassAvg(exam.getClassAvgScore() != null ? exam.getClassAvgScore() : BigDecimal.ZERO);
    analysis.setDiffFromAvg(g.getScore() - (exam.getClassAvgScore() != null ? exam.getClassAvgScore().intValue() : 0));
    analysis.setTrend(g.getScoreTrend() != null ? g.getScoreTrend() : "STABLE");
    analysis.setRank(g.getClassRank());
    
    // 1. 创建 ScoreDistributionDTO 并初始化所有计数为 0
    ScoreDistributionDTO distribution = new ScoreDistributionDTO();
    distribution.setExcellentCount(0);
    distribution.setGoodCount(0);
    distribution.setMediumCount(0);
    distribution.setPassCount(0);
    distribution.setFailCount(0);
    
    // 2. 统计分数分布
    List<Integer> scores = examGradeRepository.findScoresByExamId(exam.getId());
    if (scores != null) {
        for (Integer score : scores) {
            if (score == null) continue;
            
            if (score >= 90) {
                distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            } else if (score >= 80) {
                distribution.setGoodCount(distribution.getGoodCount() + 1);
            } else if (score >= 70) {
                distribution.setMediumCount(distribution.getMediumCount() + 1);
            } else if (score >= 60) {
                distribution.setPassCount(distribution.getPassCount() + 1);
            } else {
                distribution.setFailCount(distribution.getFailCount() + 1);
            }
        }
    }
    analysis.setDistribution(distribution);
    
    // 3. 历史成绩
    List<ExamHistoryScoreDTO> historyScores = new ArrayList<>();
    if (exam.getType() != null) {
        List<Exam> sameTypeExams = examRepository.findByType(exam.getType());
        if (sameTypeExams != null) {
            for (Exam e : sameTypeExams) {
                if (e.getId().equals(exam.getId())) continue;
                Optional<ExamGrade> historyGrade = examGradeRepository.findByExamIdAndStudentId(e.getId(), studentId);
                if (historyGrade.isPresent()) {
                    ExamHistoryScoreDTO history = new ExamHistoryScoreDTO();
                    history.setExamId(e.getId());
                    history.setExamName(e.getName());
                    history.setExamDate(e.getExamDate());
                    history.setMyScore(historyGrade.get().getScore());
                    history.setClassAvg(e.getClassAvgScore());
                    historyScores.add(history);
                }
            }
        }
    }
    analysis.setHistoryScores(historyScores);
    
    return analysis;
}
    private Map<String, BigDecimal> calculateExamClassAvgRates(Exam exam) {
        Map<String, BigDecimal> avgRates = new HashMap<>();
        List<ExamGrade> grades = examGradeRepository.findByExam(exam);
        
        if (grades.isEmpty()) return avgRates;
        
        Map<String, List<Integer>> kpScores = new HashMap<>();
        
        for (ExamGrade g : grades) {
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
}