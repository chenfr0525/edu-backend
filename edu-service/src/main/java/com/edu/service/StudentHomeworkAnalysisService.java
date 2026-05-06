package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.edu.domain.Course;
import com.edu.domain.Homework;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
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
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentHomeworkAnalysisService {
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final StudentRepository studentRepository;
    
    public Page<StudentHomeworkDTO> getStudentHomeworkList(Long studentId, Long courseId, Integer pageNum, Integer pageSize) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));

         Pageable pageable = PageRequest.of(
            pageNum != null ? pageNum : 0,
            pageSize != null ? pageSize : 10
        );
    
        Page<Homework> homeworks;
        if (courseId != null && courseId > 0) {
            homeworks = homeworkRepository.findByStudentIdAndCourseIdByPageable(studentId, courseId, pageable);
        } else {
            homeworks = homeworkRepository.findByStudentIdByPageable(studentId, pageable);
        }
       return homeworks.map(homework -> convertToStudentHomeworkDTO(homework, studentId));
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
        detail.setStatus(homework.getStatus().toString());
        detail.setDeadline(homework.getDeadline());

        detail.setMySubmission(getMySubmissionInfo(studentId, homework));
        detail.setClassStats(getClassStatistics(homework));
        detail.setKnowledgePointAnalysis(getKnowledgePointAnalysis(studentId, homework));
        detail.setScoreAnalysis(getScoreAnalysis(studentId, homework));
        
        return detail;
    }
    
    public HomeworkStatisticsCards getStudentStatisticsCards(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        HomeworkStatisticsCards cards = new HomeworkStatisticsCards();

        List<Homework>  homeworks;
         List<Submission> submissions;
        if (courseId != null && courseId > 0) {
            homeworks = homeworkRepository.findByStudentIdAndCourseId(studentId, courseId);
             submissions = submissionRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            homeworks = homeworkRepository.findByStudentId(studentId);
            submissions = submissionRepository.findByStudent(student);
        }
        
        long totalHomework = 0;
        long completedHomework = 0;
       
        totalHomework = homeworks.size();
        completedHomework=submissions.size();
        cards.setTotalCount(totalHomework);
        cards.setCompletedCount(completedHomework);
        
        double avgScore = submissions.stream()
                .filter(s -> s.getScore() != null)
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0);
        cards.setAvgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
  
        long aboveAvgCount = 0;
        for (Submission sub : submissions) {
            Homework homework = sub.getHomework();
            BigDecimal classAvg = homework.getAvgScore();
            if (classAvg != null && sub.getScore() != null && sub.getScore() > classAvg.doubleValue()) {
                aboveAvgCount++;
            }
        }
        cards.setAboveAvgCount(aboveAvgCount);
        return cards;
    }
    
    public HomeworkTrendData getStudentTrendData(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        HomeworkTrendData trendData = new HomeworkTrendData();
        
        List<Submission> submissions;
        String courseName = "全部课程";
        
        if (courseId != null && courseId > 0) {
            submissions = submissionRepository.findByStudentIdAndCourseId(studentId, courseId);
            Course course = courseService.findById(courseId).orElse(null);
            if (course != null) {
                courseName = course.getName();
            }
        } else {
            submissions = submissionRepository.findByStudent(student);
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

     /**
     * 获取或创建作业AI建议（迁移到统一服务）
     */
    private AiSuggestionDTO getOrCreateAiSuggestion(Student student, Homework homework) {
        String reportType = "HOMEWORK_ANALYSIS_" + homework.getId();
        
        return unifiedAiAnalysisService.getOrCreateAnalysis(
            "STUDENT",
            student.getId(),
            reportType,
            false
        );
    }

     /**
     * 刷新AI分析报告
     */
    public AiSuggestionDTO refreshAiAnalysis(Long studentId, Long homeworkId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("作业不存在"));
        
        String reportType = "HOMEWORK_ANALYSIS_" + homework.getId();
        
        return unifiedAiAnalysisService.refreshAnalysis(
            "STUDENT",
            student.getId(),
            reportType
        );
    }

    //降级方法
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
            info.setScore(sub.getScore());
            info.setFeedback(sub.getFeedback());
            info.setStatus(sub.getStatus().toString());
        } else {
            info.setStatus("PENDING");
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
    
    // 1. 获取作业关联的知识点ID列表
    List<Long> knowledgePointIds = homework.getKnowledgePointIds();
    if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
        log.warn("作业 {} 未关联知识点", homework.getId());
        return result;
    }
    
    // 获取知识点信息
    List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
    
    // 遍历知识点
    for (KnowledgePoint kp : kps) {
        // 获取该学生在本次作业中该知识点的得分率
        List<KnowledgePointScoreDetail> details = kpScoreDetailRepository
            .findBySourceTypeAndSourceIdAndKnowledgePointId("HOMEWORK", homework.getId(), kp.getId());
 BigDecimal myRate = BigDecimal.ZERO;
        for (KnowledgePointScoreDetail detail : details) {
            if (detail.getStudent().getId().equals(studentId)) {
                myRate = detail.getScoreRate();
                break;
            }
        }
        
      
      // 4.3 构建 DTO
        KnowledgePointMasteryDTO dto = new KnowledgePointMasteryDTO();
        dto.setKnowledgePointId(kp.getId());
        dto.setKnowledgePointName(kp.getName());
        dto.setMyScore((int)(myRate.doubleValue() / 10));
        dto.setFullScore(10);
        
       result.add(dto);
        }
    
    result.sort(Comparator.comparing(KnowledgePointMasteryDTO::getMyScore));
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
}