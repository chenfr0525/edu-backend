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

import com.edu.common.PageResult;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.ScorePrediction;
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
import com.edu.repository.ScorePredictionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentExamAnalysisService {
   private final ExamRepository examRepository;
    private final ExamGradeRepository examGradeRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final SemesterService semesterService;
    private final AiAnalysisReportService aiReportService;
    private final ObjectMapper objectMapper;
    private final KnowledgePointRepository knowledgePointRepository;

    /**
     * 1. 获取学生的考试列表（分页）
     */
    public PageResult<StudentExamDTO> getStudentExamListPage(
            Long studentId, Long courseId, String status, int pageNum, int pageSize) {
        
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 获取所有考试
        List<Exam> allExams;
        if (courseId != null && courseId > 0) {
            allExams = examRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            allExams = examRepository.findByStudentIdAndCompleted(studentId);
        }
        
        // 状态筛选
        if (status != null && !status.isEmpty()) {
            allExams = allExams.stream()
                    .filter(e -> e.getStatus().equals(status))
                    .collect(Collectors.toList());
        }
        
        // 转换为DTO
        List<StudentExamDTO> allDTOs = allExams.stream()
                .map(exam -> convertToStudentExamDTO(exam, studentId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        // 分页
        int total = allDTOs.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
     
      List<StudentExamDTO> examList = allDTOs.subList(start, end);

      return new PageResult<>(examList, (long) total, pageNum, pageSize);
    }
    
    /**
     * 2. 获取学生单次考试的详细分析
     */
    @Transactional
    public StudentExamDetailDTO getStudentExamDetail(Long studentId, Long examId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("考试不存在"));
        
        StudentExamDetailDTO detail = new StudentExamDetailDTO();
        
        // 基础信息
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
        
        // 我的考试成绩
        detail.setMyGrade(getMyExamGradeInfo(studentId, exam));
        
        // 班级统计
        detail.setClassStats(getExamClassStatistics(exam));
        
        // 知识点得分分析
        detail.setKnowledgePointAnalysis(getExamKnowledgePointAnalysis(studentId, exam));
        
        // 成绩分析
        detail.setScoreAnalysis(getExamScoreAnalysis(studentId, exam));
        
        // AI个性化建议
        detail.setAiSuggestion(getOrCreateExamAiSuggestion(student, exam));
        
        return detail;
    }
    
    /**
     * 3. 获取学生考试统计卡片
     */
    public ExamStatisticsCards getStudentExamStatisticsCards(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        ExamStatisticsCards cards = new ExamStatisticsCards();
        
        // 获取所有考试成绩
        List<ExamGrade> examGrades = examGradeRepository.findAllByStudentIdOrderByDateAsc(studentId);
        
        if (examGrades.isEmpty()) {
            cards.setAvgScore(BigDecimal.ZERO);
            cards.setAvgRank(BigDecimal.ZERO);
            cards.setTotalExams(0);
           cards.setAboveAvgCount(0);
            cards.setAboveAvgRate(BigDecimal.ZERO);
            return cards;
        }
        
        // 1. 考试平均分
        double avgScore = examGrades.stream()
                .mapToInt(ExamGrade::getScore)
                .average()
                .orElse(0);
        cards.setAvgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        
        // 2. 平均排名
        double avgRank = examGrades.stream()
                .filter(eg -> eg.getClassRank() != null)
                .mapToInt(ExamGrade::getClassRank)
                .average()
                .orElse(0);
        cards.setAvgRank(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
        
        // 3. 总考试次数
        cards.setTotalExams(examGrades.size());
        
        // 4. 高于班级平均的考试次数
        long aboveAvgCount = examGradeRepository.countAboveClassAvg(studentId);
        cards.setAboveAvgCount((int)aboveAvgCount);
        cards.setAboveAvgRate(BigDecimal.valueOf(aboveAvgCount * 100.0 / examGrades.size())
                .setScale(2, RoundingMode.HALF_UP));
        
        // 额外：最佳科目和薄弱科目
        Map<Long, Double> courseAvgScores = new HashMap<>();
        for (ExamGrade eg : examGrades) {
            Long courseId = eg.getExam().getCourse().getId();
            courseAvgScores.merge(courseId, (double) eg.getScore(), Double::sum);
        }
        
        // 计算每门课的平均分并找出最佳/最差
        // 简化处理，实际需要按课程统计
        cards.setBestSubject("待计算");
        cards.setWeakestSubject("待计算");
        
        return cards;
    }
    
    /**
     * 4. 获取学生考试趋势图数据
     */
    public ExamTrendData getStudentExamTrendData(Long studentId, Long courseId) {
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
        
        // 计算趋势
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
    
    /**
     * 5. 获取学生整体考试AI分析报告
     */
    public Map<String, Object> getStudentExamOverallAnalysis(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 尝试从缓存获取（7天内有效）
        AiAnalysisReport cachedReport = aiReportService.findLatestReport("STUDENT_EXAM", studentId, "OVERALL");
        
        if (cachedReport != null && cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            Map<String, Object> result = new HashMap<>();
            result.put("summary", cachedReport.getSummary());
            result.put("suggestions", cachedReport.getSuggestions());
            result.put("analysisData", cachedReport.getAnalysisData());
            result.put("createdAt", cachedReport.getCreatedAt());
            result.put("fromCache", true);
            return result;
        }
        
        // 生成新的分析报告（模拟AI）
        Map<String, Object> analysis = generateExamOverallAnalysis(student);
        
        // 存储到数据库
        Semester currentSemester = getCurrentSemester();
        if (currentSemester != null) {
            AiAnalysisReport report = new AiAnalysisReport();
            report.setTargetType("STUDENT");
            report.setTargetId(studentId);
            report.setSemester(currentSemester);
            report.setReportType("EXAM");
            report.setSummary((String) analysis.get("summary"));
            report.setSuggestions((String) analysis.get("suggestions"));
            report.setCreatedAt(LocalDateTime.now());
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
            aiReportService.save(report);
        }
        
        analysis.put("fromCache", false);
        return analysis;
    }
    
    /**
     * 6. 获取即将到来的考试提醒
     */
    public List<Map<String, Object>> getUpcomingExams(Long studentId) {
        List<Exam> upcomingExams = examRepository.findUpcomingByStudentId(studentId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Exam exam : upcomingExams) {
            Map<String, Object> examInfo = new HashMap<>();
            examInfo.put("examId", exam.getId());
            examInfo.put("examName", exam.getName());
            examInfo.put("examDate", exam.getExamDate());
            examInfo.put("courseName", exam.getCourse().getName());
            examInfo.put("location", exam.getLocation());
            
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exam.getExamDate());
            examInfo.put("daysLeft", daysLeft);
            
            if (daysLeft <= 3) {
                examInfo.put("urgent", true);
            } else {
                examInfo.put("urgent", false);
            }
            
            result.add(examInfo);
        }
        
        return result;
    }
    
    // ==================== 私有辅助方法 ====================
    
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
        
        // 获取所有成绩
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
        
        // 及格率和优秀率
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
        
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        if (grade.isPresent()|| grade.get().getKnowledgePointScores() == null) {
            return result;
        }
        
        Map<String, Integer> myKpScores = parseKnowledgePointScores(grade.get().getKnowledgePointScores());
        
        // 计算班级各知识点的平均得分率
        Map<String, BigDecimal> classAvgRates = calculateExamClassAvgRates(exam);
        
        for (Map.Entry<String, Integer> entry : myKpScores.entrySet()) {
            String kpId = entry.getKey();
            Integer myScore = entry.getValue();
            
            ExamKnowledgePointDTO dto = new ExamKnowledgePointDTO();
            dto.setKnowledgePointId(Long.parseLong(kpId));
            dto.setKnowledgePointName(getKnowledgePointName(Long.parseLong(kpId)));
            dto.setMyScore(myScore);
            dto.setFullScore(10);
            dto.setScoreRate(BigDecimal.valueOf(myScore * 10.0).setScale(2, RoundingMode.HALF_UP));
            
            BigDecimal classAvg = classAvgRates.getOrDefault(kpId, BigDecimal.ZERO);
            dto.setClassAvgRate(classAvg);
            
            // 判断掌握等级
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
        
        result.sort(Comparator.comparing(ExamKnowledgePointDTO::getScoreRate));
        
        return result;
    }
    
    private ExamScoreAnalysisDTO getExamScoreAnalysis(Long studentId, Exam exam) {
        ExamScoreAnalysisDTO analysis = new ExamScoreAnalysisDTO();
        
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        if (grade.isPresent()) {
            return analysis;
        }
        
        ExamGrade g = grade.get();
        analysis.setMyScore(g.getScore());
        analysis.setClassAvg(exam.getClassAvgScore() != null ? exam.getClassAvgScore() : BigDecimal.ZERO);
        analysis.setDiffFromAvg(g.getScore() - (exam.getClassAvgScore() != null ? exam.getClassAvgScore().intValue() : 0));
        analysis.setTrend(g.getScoreTrend() != null ? g.getScoreTrend() : "STABLE");
        analysis.setRank(g.getClassRank());
        
        // 分数段分布
        ScoreDistributionDTO distribution = new ScoreDistributionDTO();
        List<Integer> scores = examGradeRepository.findScoresByExamId(exam.getId());
        for (Integer score : scores) {
            if (score >= 90) distribution.setExcellentCount(distribution.getExcellentCount() + 1);
            else if (score >= 80) distribution.setGoodCount(distribution.getGoodCount() + 1);
            else if (score >= 70) distribution.setMediumCount(distribution.getMediumCount() + 1);
            else if (score >= 60) distribution.setPassCount(distribution.getPassCount() + 1);
            else distribution.setFailCount(distribution.getFailCount() + 1);
        }
        analysis.setDistribution(distribution);
        
        // 历次同类考试对比
        List<ExamHistoryScoreDTO> historyScores = new ArrayList<>();
        List<Exam> sameTypeExams = examRepository.findByType(exam.getType().toString());
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
        analysis.setHistoryScores(historyScores);
        
        return analysis;
    }
    
    private AiSuggestionDTO getOrCreateExamAiSuggestion(Student student, Exam exam) {
        AiSuggestionDTO suggestion = new AiSuggestionDTO();
        
        Optional<ExamGrade> grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId());
        
        if (grade.isPresent() || grade.get().getScore() == null) {
            suggestion.setSummary("考试尚未出成绩，暂无法生成分析建议");
            suggestion.setStrengths(Arrays.asList("待出成绩后查看"));
            suggestion.setWeaknesses(Arrays.asList("待出成绩后查看"));
            suggestion.setActionItems(Arrays.asList("请耐心等待成绩公布"));
            suggestion.setNextStep("成绩公布后可查看详细分析");
            return suggestion;
        }
        
        ExamGrade g = grade.get();
        int score = g.getScore();
        BigDecimal classAvg = exam.getClassAvgScore();
        
        // 根据分数生成建议
        if (score >= 90) {
            suggestion.setSummary("本次考试表现优秀！知识点掌握扎实，解题思路清晰。");
            suggestion.setStrengths(Arrays.asList("基础知识牢固", "解题规范", "时间分配合理"));
            suggestion.setWeaknesses(Arrays.asList("可挑战更高难度题目"));
            suggestion.setActionItems(Arrays.asList("继续保持学习节奏", "尝试帮助其他同学", "预习下一阶段内容"));
            suggestion.setNextStep("建议参加学科竞赛提升能力");
        } else if (score >= 75) {
            suggestion.setSummary("本次考试表现良好，基础知识点掌握不错，部分细节需要完善。");
            suggestion.setStrengths(Arrays.asList("基础题正确率高", "考试态度认真"));
            suggestion.setWeaknesses(Arrays.asList("部分综合题扣分", "审题需更仔细"));
            suggestion.setActionItems(Arrays.asList("复习错题相关知识点", "多做同类题型练习", "总结易错点"));
            suggestion.setNextStep("重点突破薄弱知识点");
        } else if (score >= 60) {
            suggestion.setSummary("本次考试成绩及格，但仍有提升空间，建议加强薄弱环节。");
            suggestion.setStrengths(Arrays.asList("参与度高", "完成度良好"));
            suggestion.setWeaknesses(Arrays.asList("基础概念理解不深", "解题步骤不完整"));
            suggestion.setActionItems(Arrays.asList("重新复习课堂笔记", "整理错题本", "向老师/同学请教"));
            suggestion.setNextStep("建议每天安排30分钟针对性复习");
        } else {
            suggestion.setSummary("本次考试成绩不理想，需要重点关注基础知识的巩固。");
            suggestion.setStrengths(Arrays.asList("愿意参加考试"));
            suggestion.setWeaknesses(Arrays.asList("基础概念模糊", "解题方法不当", "练习量不足"));
            suggestion.setActionItems(Arrays.asList("回顾课堂内容", "完成基础练习题", "寻求老师辅导", "与同学组成学习小组"));
            suggestion.setNextStep("建议从最基础的知识点开始复习");
        }
        
        // 加入班级对比
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
      Optional<KnowledgePoint> knowledgePoint=knowledgePointRepository.findById(kpId);
      if (knowledgePoint.isPresent()) {
        return knowledgePoint.get().getName();  // 用 get() 取出对象
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
    
    private Map<String, Object> generateExamOverallAnalysis(Student student) {
        List<ExamGrade> examGrades = examGradeRepository.findAllByStudentIdOrderByDateAsc(student.getId());
        
        if (examGrades.isEmpty()) {
            Map<String, Object> analysisData = new HashMap<>();
             analysisData.put(   "summary", "暂无考试数据，无法生成分析报告");
             analysisData.put(    "suggestions", "参加考试后可查看分析");
            return analysisData;
        }
        
        double avgScore = examGrades.stream().mapToInt(ExamGrade::getScore).average().orElse(0);
        long aboveAvgCount = examGradeRepository.countAboveClassAvg(student.getId());
        long improvingCount = examGrades.stream()
                .filter(eg -> "UP".equals(eg.getScoreTrend()))
                .count();
        
        StringBuilder summary = new StringBuilder();
        summary.append("本学期共参加 ").append(examGrades.size()).append(" 次考试，");
        summary.append("平均分 ").append(String.format("%.1f", avgScore)).append(" 分，");
        summary.append("其中 ").append(aboveAvgCount).append(" 次考试高于班级平均水平，");
        summary.append(improvingCount).append(" 次考试成绩呈上升趋势。");
        
        if (avgScore >= 85) {
            summary.append("整体表现优秀，继续保持！");
        } else if (avgScore >= 70) {
            summary.append("整体表现良好，部分知识点可加强。");
        } else {
            summary.append("整体有待提升，建议加强课后复习。");
        }
        
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("1. 针对薄弱知识点进行专项练习\n");
        suggestions.append("2. 考前制定复习计划，合理分配时间\n");
        suggestions.append("3. 分析错题原因，建立错题本\n");
        
        if (avgScore < 70) {
            suggestions.append("4. 建议每周至少复习3次，每次1小时\n");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary.toString());
        result.put("suggestions", suggestions.toString());
        result.put("totalExams", examGrades.size());
        result.put("avgScore", avgScore);
        result.put("aboveAvgCount", aboveAvgCount);
        
        return result;
    }
}
