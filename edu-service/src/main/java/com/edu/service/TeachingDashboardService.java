package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.edu.domain.ActivityAlert;
import com.edu.domain.ActivityRecord;
import com.edu.domain.ClassInfo;
import com.edu.domain.ClassWrongQuestionStats;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.ExamStatus;
import com.edu.domain.Homework;
import com.edu.domain.HomeworkStatus;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.Submission;
import com.edu.domain.Teacher;
import com.edu.domain.dto.ActivityMonitorDTO;
import com.edu.domain.dto.ClassScoreDistributionDTO;
import com.edu.domain.dto.CriticalAlertDTO;
import com.edu.domain.dto.DashboardStatsDTO;
import com.edu.domain.dto.LowActivityStudentDTO;
import com.edu.domain.dto.TeachingDashboardDataDTO;
import com.edu.domain.dto.WeakKnowledgePointDTO;
import com.edu.domain.dto.WeakStudentDTO;
import com.edu.domain.dto.WrongQuestionDTO;
import com.edu.repository.ActivityAlertRepository;
import com.edu.repository.ActivityRecordRepository;
import com.edu.repository.ClassRepository;
import com.edu.repository.ClassWrongQuestionStatsRepository;
import com.edu.repository.CourseRepository;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.HomeworkRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.SubmissionRepository;
import com.edu.repository.TeacherRepository;
import com.edu.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeachingDashboardService {
   private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final ExamGradeRepository examGradeRepository;
    private final HomeworkRepository homeworkRepository;
    private final ExamRepository examRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ClassWrongQuestionStatsRepository wrongQuestionStatsRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityAlertRepository activityAlertRepository;
    private final ActivityRecordService activityRecordService;
    private final  UserRepository userRepository;  

    /**
     * 获取教师可见的班级列表
     */
    public List<ClassInfo> getTeacherClasses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return new ArrayList<>();
        }
        return classRepository.findByTeacher(teacher);
    }

    /**
     * 获取教师可见的课程列表（通过所教班级关联）
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return new ArrayList<>();
        }
        // 教师所教的课程
        return courseRepository.findByTeacher(teacher);
    }

    /**
     * 获取管理员可见的所有班级
     */
    public List<ClassInfo> getAllClasses() {
        return classRepository.findAll();
    }

    /**
     * 获取管理员可见的所有课程
     */
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    /**
     * 获取教学看板数据
     */
    @Transactional(readOnly = true)
    public TeachingDashboardDataDTO getDashboardData(
            Long currentUserId, String userRole,
            Long classId, Long courseId) {
        
        // 1. 确定查询的班级和课程
        List<Long> classIds = resolveClassIds(currentUserId, userRole, classId);
        List<Long> courseIds = resolveCourseIds(currentUserId, userRole, courseId);
        
        // 2. 获取状态卡片数据
        DashboardStatsDTO stats = buildStats(classIds, courseIds);
        
        // 3. 获取成绩分布
        List<ClassScoreDistributionDTO> scoreDistributions = 
            buildScoreDistributions(classIds, courseIds);
        
        // 4. 获取高频错题排行
        List<WrongQuestionDTO> topWrongQuestions = 
            buildTopWrongQuestions(classIds, courseIds);
        
        // 5. 获取薄弱知识点
        List<WeakKnowledgePointDTO> weakKnowledgePoints = 
            buildWeakKnowledgePoints(classIds, courseIds);
        
        // 6. 获取活跃度监控
        ActivityMonitorDTO activityMonitor = 
            buildActivityMonitor(classIds, courseIds);
        
        return TeachingDashboardDataDTO.builder()
            .selectedClassIds(classIds)
            .selectedCourseIds(courseIds)
            .stats(stats)
            .scoreDistributions(scoreDistributions)
            .topWrongQuestions(topWrongQuestions)
            .weakKnowledgePoints(weakKnowledgePoints)
            .activityMonitor(activityMonitor)
            .build();
    }

    /**
     * 解析可见班级ID
     */
    private List<Long> resolveClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) {
                return Arrays.asList(requestClassId);
            }
            // 管理员查看所有班级
            return classRepository.findAll().stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        } else {
            // 教师：只能看自己教的班级
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) {
                return new ArrayList<>();
            }
            List<ClassInfo> teacherClasses = classRepository.findByTeacher(teacher);
            if (requestClassId != null) {
                boolean hasAccess = teacherClasses.stream()
                    .anyMatch(c -> c.getId().equals(requestClassId));
                return hasAccess ? Arrays.asList(requestClassId) : new ArrayList<>();
            }
            return teacherClasses.stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        }
    }

    /**
     * 解析可见课程ID
     */
    private List<Long> resolveCourseIds(Long userId, String userRole, Long requestCourseId) {
        if ("ADMIN".equals(userRole)) {
            if (requestCourseId != null) {
                return Arrays.asList(requestCourseId);
            }
            return courseRepository.findAll().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) {
                return new ArrayList<>();
            }
            List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
            if (requestCourseId != null) {
                boolean hasAccess = teacherCourses.stream()
                    .anyMatch(c -> c.getId().equals(requestCourseId));
                return hasAccess ? Arrays.asList(requestCourseId) : new ArrayList<>();
            }
            return teacherCourses.stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        }
    }

    /**
     * 构建状态卡片数据
     */
    private DashboardStatsDTO buildStats(List<Long> classIds, List<Long> courseIds) {
        // 获取相关学生
        List<Student> students = getStudentsByClasses(classIds);
        
        // 统计学生总数
        long studentCount = students.size();
        
        // 统计班级数
        long classCount = classIds.size();
        
        // 统计课程数
        long courseCount = courseIds.size();
        
        // 待批改作业数
        long pendingHomeworkCount = 0;
        for (Long courseId : courseIds) {
            List<Homework> pendingHomeworks = homeworkRepository.findByCourseAndStatus(
                courseRepository.findById(courseId).orElse(null), HomeworkStatus.PENDING);
            pendingHomeworkCount += pendingHomeworks.size();
        }
        
        // 即将开始的考试数
        long upcomingExamCount = 0;
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo != null) {
                List<Exam> upcomingExams = examRepository.findByExamDateAfterAndStatus(
                    LocalDate.now().atStartOfDay(), ExamStatus.UPCOMING);
                upcomingExamCount += upcomingExams.stream()
                    .filter(e -> e.getClassInfo().getId().equals(classId))
                    .count();
            }
        }
        
        // 低活跃度预警数
        long lowActivityAlertCount = 0;
        for (Long classId : classIds) {
            lowActivityAlertCount += activityAlertRepository.findUnresolvedByClassId(classId).size();
        }
        
        // 薄弱知识点数
        long weakPointCount = 0;
        for (Student student : students) {
            List<StudentKnowledgeMastery> weakPoints = masteryRepository.findByStudentAndMasteryLevelLessThan(
                student,  BigDecimal.valueOf(60).doubleValue());
            weakPointCount += weakPoints.size();
        }
        
        // 整体平均分和及格率
        double totalScore = 0;
        int scoreCount = 0;
        int passCount = 0;
        
        for (Long courseId : courseIds) {
            List<ExamGrade> grades = examGradeRepository.findByCourseId(courseId);
            for (ExamGrade grade : grades) {
                if (grade.getScore() != null) {
                    totalScore += grade.getScore().doubleValue();
                    scoreCount++;
                    if (grade.getScore().doubleValue() >= 60) {
                        passCount++;
                    }
                }
            }
        }
        
        double overallAvgScore = scoreCount > 0 ? totalScore / scoreCount : 0;
        double overallPassRate = scoreCount > 0 ? (passCount * 100.0 / scoreCount) : 0;
        
        return DashboardStatsDTO.builder()
            .studentCount(studentCount)
            .teacherCount(0L)  // 暂不统计
            .classCount(classCount)
            .courseCount(courseCount)
            .pendingHomeworkCount(pendingHomeworkCount)
            .upcomingExamCount(upcomingExamCount)
            .lowActivityAlertCount(lowActivityAlertCount)
            .weakPointCount(weakPointCount)
            .overallAvgScore(Math.round(overallAvgScore * 100) / 100.0)
            .overallPassRate(Math.round(overallPassRate * 100) / 100.0)
            .build();
    }

    /**
     * 构建成绩分布
     */
    private List<ClassScoreDistributionDTO> buildScoreDistributions(
            List<Long> classIds, List<Long> courseIds) {
        
        List<ClassScoreDistributionDTO> result = new ArrayList<>();
        
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo == null) continue;
            
            List<Student> students = studentRepository.findByClassInfo(classInfo);
            
            // 收集该班级所有相关课程的成绩
            List<Double> allScores = new ArrayList<>();
            for (Long courseId : courseIds) {
                List<ExamGrade> grades = examGradeRepository.findByClassIdAndCourseId(classId, courseId);
                for (ExamGrade grade : grades) {
                    if (grade.getScore() != null) {
                        allScores.add(grade.getScore().doubleValue());
                    }
                }
            }
            
            if (allScores.isEmpty()) continue;
            
            // 成绩分布统计
            Map<String, Integer> distribution = new LinkedHashMap<>();
            distribution.put("优秀(>=90)", 0);
            distribution.put("良好(80-89)", 0);
            distribution.put("中等(70-79)", 0);
            distribution.put("及格(60-69)", 0);
            distribution.put("不及格(<60)", 0);
            
            double totalScore = 0;
            double highest = 0;
            double lowest = 100;
            int passCount = 0;
            int excellentCount = 0;
            
            for (Double score : allScores) {
                totalScore += score;
                highest = Math.max(highest, score);
                lowest = Math.min(lowest, score);
                
                if (score >= 90) {
                    distribution.put("优秀(>=90)", distribution.get("优秀(>=90)") + 1);
                    excellentCount++;
                } else if (score >= 80) {
                    distribution.put("良好(80-89)", distribution.get("良好(80-89)") + 1);
                } else if (score >= 70) {
                    distribution.put("中等(70-79)", distribution.get("中等(70-79)") + 1);
                } else if (score >= 60) {
                    distribution.put("及格(60-69)", distribution.get("及格(60-69)") + 1);
                    passCount++;
                } else {
                    distribution.put("不及格(<60)", distribution.get("不及格(<60)") + 1);
                }
            }
            
            // 计算标准差
            double avg = totalScore / allScores.size();
            double variance = allScores.stream()
                .mapToDouble(s -> Math.pow(s - avg, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            result.add(ClassScoreDistributionDTO.builder()
                .classId(classId)
                .className(classInfo.getName())
                .grade(classInfo.getGrade())
                .distribution(distribution)
                .averageScore(Math.round(avg * 100) / 100.0)
                .highestScore(highest)
                .lowestScore(lowest)
                .passRate(allScores.isEmpty() ? 0 : (passCount * 100.0 / allScores.size()))
                .excellentRate(allScores.isEmpty() ? 0 : (excellentCount * 100.0 / allScores.size()))
                .studentCount(students.size())
                .standardDeviation(Math.round(stdDev * 100) / 100.0)
                .build());
        }
        
        return result;
    }

    /**
     * 构建高频错题排行
     */
    private List<WrongQuestionDTO> buildTopWrongQuestions(List<Long> classIds, List<Long> courseIds) {
        List<WrongQuestionDTO> result = new ArrayList<>();
        
        for (Long courseId : courseIds) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) continue;
            
            for (Long classId : classIds) {
                List<ClassWrongQuestionStats> stats = 
                    wrongQuestionStatsRepository.findByClassIdAndCourseIdOrderByErrorRateDesc(classId, courseId);
                
                for (ClassWrongQuestionStats stat : stats) {
                    result.add(WrongQuestionDTO.builder()
                        .knowledgePointId(stat.getKnowledgePoint().getId())
                        .knowledgePointName(stat.getKnowledgePoint().getName())
                        .courseId(courseId)
                        .courseName(course.getName())
                        .errorCount(stat.getErrorCount())
                        .totalStudents(stat.getTotalStudents())
                        .errorRate(stat.getErrorRate())
                        .rank(stat.getRankInClass())
                        .build());
                }
            }
        }
        
        // 按错误率排序，取前10
        result.sort((a, b) -> b.getErrorRate().compareTo(a.getErrorRate()));
        return result.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * 构建薄弱知识点
     */
    private List<WeakKnowledgePointDTO> buildWeakKnowledgePoints(List<Long> classIds, List<Long> courseIds) {
        List<WeakKnowledgePointDTO> result = new ArrayList<>();
        
        for (Long courseId : courseIds) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) continue;
            
            // 按知识点分组统计
            Map<Long, WeakPointAggregate> aggregateMap = new HashMap<>();
            
            for (Long classId : classIds) {
                List<StudentKnowledgeMastery> masteries = 
                    masteryRepository.findByClassIdAndCourseId(classId, courseId);
                
                for (StudentKnowledgeMastery mastery : masteries) {
                    Long kpId = mastery.getKnowledgePoint().getId();
                    aggregateMap.putIfAbsent(kpId, new WeakPointAggregate());
                    WeakPointAggregate agg = aggregateMap.get(kpId);
                    agg.knowledgePointName = mastery.getKnowledgePoint().getName();
                    agg.knowledgePointId = kpId;
                    agg.scores.add(mastery.getMasteryLevel().doubleValue());
                    
                    if (mastery.getMasteryLevel().doubleValue() < 60) {
                        agg.weakStudents.add(WeakStudentDTO.builder()
                            .studentId(mastery.getStudent().getId())
                            .studentName(mastery.getStudent().getUser().getName())
                            .studentNo(mastery.getStudent().getStudentNo())
                            .masteryLevel(mastery.getMasteryLevel().doubleValue())
                            .build());
                    }
                }
            }
            
            // 筛选平均掌握度低于70%的知识点
            for (Map.Entry<Long, WeakPointAggregate> entry : aggregateMap.entrySet()) {
                WeakPointAggregate agg = entry.getValue();
                double avgMastery = agg.scores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(100);
                
                if (avgMastery < 70) {
                    result.add(WeakKnowledgePointDTO.builder()
                        .knowledgePointId(agg.knowledgePointId)
                        .knowledgePointName(agg.knowledgePointName)
                        .courseId(courseId)
                        .courseName(course.getName())
                        .avgMastery(Math.round(avgMastery * 100) / 100.0)
                        .studentCount(agg.scores.size())
                        .affectedRate(Math.round((agg.weakStudents.size() * 100.0 / agg.scores.size()) * 100) / 100.0)
                        .weakStudents(agg.weakStudents.stream().limit(5).collect(Collectors.toList()))
                        .build());
                }
            }
        }
        
        // 按平均掌握度排序
        result.sort((a, b) -> Double.compare(a.getAvgMastery(), b.getAvgMastery()));
        return result.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * 构建活跃度监控
     */
    private ActivityMonitorDTO buildActivityMonitor(List<Long> classIds, List<Long> courseIds) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate twoWeeksAgo = today.minusDays(14);
        
        // 获取相关学生
        List<Student> students = getStudentsByClasses(classIds);
        Set<Long> studentIds = students.stream()
            .map(Student::getId)
            .collect(Collectors.toSet());
        
        // 计算本周和上周平均活跃度
        double thisWeekTotal = 0;
        double lastWeekTotal = 0;
        int thisWeekCount = 0;
        int lastWeekCount = 0;
        
        // 低活跃度学生统计
        List<LowActivityStudentDTO> lowActivityStudents = new ArrayList<>();
        long lowActivityCount = 0;
        
        for (Student student : students) {
            Double activityScore = activityRecordService.getStudentTotalActivityScore(student.getId());
            if (activityScore == null) activityScore = 0.0;
            
            // 本周活跃度
            List<ActivityRecord> thisWeekRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), today.atStartOfDay());
            double thisWeekScore = thisWeekRecords.stream()
                .mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0)
                .sum();
            thisWeekTotal += thisWeekScore;
            thisWeekCount++;
            
            // 上周活跃度
            List<ActivityRecord> lastWeekRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, twoWeeksAgo.atStartOfDay(), weekAgo.atStartOfDay());
            double lastWeekScore = lastWeekRecords.stream()
                .mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0)
                .sum();
            lastWeekTotal += lastWeekScore;
            lastWeekCount++;
            
            // 低活跃度判断（本周活跃度低于20）
            if (thisWeekScore < 20) {
                lowActivityCount++;
                Integer studyDuration = activityRecordService.getStudentStudyDuration(
                    student.getId(), weekAgo);
                lowActivityStudents.add(LowActivityStudentDTO.builder()
                    .studentId(student.getId())
                    .studentName(student.getUser().getName())
                    .studentNo(student.getStudentNo())
                    .activityScore(thisWeekScore)
                    .studyDuration(studyDuration != null ? studyDuration : 0)
                    .build());
            }
        }
        
        double thisWeekAvg = thisWeekCount > 0 ? thisWeekTotal / thisWeekCount : 0;
        double lastWeekAvg = lastWeekCount > 0 ? lastWeekTotal / lastWeekCount : 0;
        
        // 严重预警
        List<CriticalAlertDTO> criticalAlerts = new ArrayList<>();
        long criticalAlertCount = 0;
        
        for (Long classId : classIds) {
            List<ActivityAlert> alerts = activityAlertRepository.findUnresolvedByClassId(classId);
            for (ActivityAlert alert : alerts) {
                if ("CRITICAL".equals(alert.getAlertLevel())) {
                    criticalAlertCount++;
                    criticalAlerts.add(CriticalAlertDTO.builder()
                        .alertId(alert.getId())
                        .studentId(alert.getStudent().getId())
                        .studentName(alert.getStudent().getUser().getName())
                        .studentNo(alert.getStudent().getStudentNo())
                        .alertType(alert.getAlertType())
                        .alertLevel(alert.getAlertLevel())
                        .activityScore(alert.getActivityScore())
                        .threshold(alert.getThreshold())
                        .build());
                }
            }
        }
        
        // 活跃学生数（本周活跃度>0）
        long activeStudentCount = students.stream()
            .filter(s -> {
                List<ActivityRecord> records = activityRecordRepository
                    .findByStudentAndActivityDateBetween(s, weekAgo.atStartOfDay(), today.atStartOfDay());
                return !records.isEmpty();
            })
            .count();
        
        return ActivityMonitorDTO.builder()
            .classAvgActivityScore(Math.round(thisWeekAvg * 100) / 100.0)
            .thisWeekAvgActivity(Math.round(thisWeekAvg * 100) / 100.0)
            .lastWeekAvgActivity(Math.round(lastWeekAvg * 100) / 100.0)
            .activityChange(Math.round((thisWeekAvg - lastWeekAvg) * 100) / 100.0)
            .activeStudentCount(activeStudentCount)
            .lowActivityCount(lowActivityCount)
            .lowActivityStudents(lowActivityStudents)
            .criticalAlertCount(criticalAlertCount)
            .criticalAlerts(criticalAlerts)
            .build();
    }

    /**
     * 获取班级下的学生列表
     */
    private List<Student> getStudentsByClasses(List<Long> classIds) {
        List<Student> students = new ArrayList<>();
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo != null) {
                students.addAll(studentRepository.findByClassInfo(classInfo));
            }
        }
        return students;
    }

    /**
     * 内部聚合类
     */
    private static class WeakPointAggregate {
        Long knowledgePointId;
        String knowledgePointName;
        List<Double> scores = new ArrayList<>();
        List<WeakStudentDTO> weakStudents = new ArrayList<>();
    }
}
