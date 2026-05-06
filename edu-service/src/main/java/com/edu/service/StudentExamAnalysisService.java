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
import com.edu.domain.KnowledgePointScoreDetail;
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
import com.edu.repository.KnowledgePointScoreDetailRepository;

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
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;

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
    if (exam == null) {
        log.warn("exam is null");
        return null;
    }
    
    Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
    
    StudentExamDTO dto = new StudentExamDTO();
    dto.setId(exam.getId());
    dto.setName(exam.getName());
    dto.setType(exam.getType().toString());
    
    // 处理 course 可能为 null 的情况
    if (exam.getCourse() != null) {
        dto.setCourseName(exam.getCourse().getName());
        dto.setCourseId(exam.getCourse().getId());
    }
    
    dto.setExamDate(exam.getExamDate());
    dto.setFullScore(exam.getFullScore());
    dto.setStatus(exam.getStatus().toString());
    
    // 如果有成绩，设置成绩相关信息
    if (grade.isPresent()) {
        ExamGrade g = grade.get();
        dto.setMyScore(g.getScore());
        dto.setClassRank(g.getClassRank());
        dto.setScoreTrend(g.getScoreTrend());
    } else {
        // 没有成绩时设置默认值
        dto.setMyScore(null);
        dto.setClassRank(null);
        dto.setScoreTrend(null);
        log.debug("学生 {} 在考试 {} 中没有成绩记录", studentId, exam.getId());
    }
    
    // 班级平均分可能为 null
    dto.setClassAvgScore(exam.getClassAvgScore());
    
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
            info.setScoreTrend(g.getScoreTrend());
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
    
        // 参数校验
        if (studentId == null || exam == null) {
            log.warn("参数为空: studentId={}, exam={}", studentId, exam);
            return result;
        }
        
        // 1. 获取考试关联的知识点ID列表
        List<Long> knowledgePointIds = exam.getKnowledgePointIds();
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            log.warn("考试 {} 未关联知识点", exam.getId());
            return result;
        }
        
        // 2. 获取知识点信息
        List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
    
        // 3. 获取班级ID（用于计算班级平均分）
        Long classId = null;
        if (exam.getClassInfo() != null) {
            classId = exam.getClassInfo().getId();
        }

         // 4. 遍历知识点，从 knowledge_point_score_detail 获取数据
    for (KnowledgePoint kp : kps) {
        // 4.1 获取该学生在本次考试中该知识点的得分率
        List<KnowledgePointScoreDetail> details = kpScoreDetailRepository
            .findBySourceTypeAndSourceIdAndKnowledgePointId("EXAM", exam.getId(), kp.getId());
        
        // 过滤出当前学生的记录
        BigDecimal myRate = BigDecimal.ZERO;
        for (KnowledgePointScoreDetail detail : details) {
            if (detail.getStudent().getId().equals(studentId)) {
                myRate = detail.getScoreRate();
                break;
            }
        }
   // 4.2 获取班级平均得分率
        BigDecimal classAvgRate = BigDecimal.ZERO;
        if (classId != null) {
            classAvgRate = kpScoreDetailRepository.getClassAvgScoreRate(kp.getId(), classId);
            if (classAvgRate == null) classAvgRate = BigDecimal.ZERO;
        }
        
        // 4.3 计算等级和建议（基于得分率）
        double myRateValue = myRate.doubleValue();
        String level;
        String suggestion;
        if (myRateValue >= 80) {
            level = "GOOD";
            suggestion = "✅ 掌握良好，继续保持";
        } else if (myRateValue >= 60) {
            level = "MODERATE";
            suggestion = "📚 基本掌握，建议加强练习";
        } else {
            level = "WEAK";
            suggestion = "🔴 薄弱知识点，需要重点复习";
        }
    // 4.4 构建 DTO
        ExamKnowledgePointDTO dto = ExamKnowledgePointDTO.builder()
            .knowledgePointId(kp.getId())
            .knowledgePointName(kp.getName())
            .fullScore(10)  // 10分制
            .myScore((int) (myRateValue / 10))  // 百分制转10分制
            .scoreRate(myRate)
            .classAvgRate(classAvgRate)
            .level(level)
            .suggestion(suggestion)
            .build();
        
        result.add(dto);
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
    List<ExamGrade> allScores = examGradeRepository.findByExamOrderByScoreDesc(exam);
    int rank = 1;
    for (int i = 0; i < allScores.size(); i++) {
        if (allScores.get(i).getStudent().getId().equals(studentId)) {
            rank = i + 1;
            break;
        }
    }
    analysis.setRank(rank);
    
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
}