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
    private final ActivityRecordRepository activityRecordRepository;
    private final SubmissionRepository submissionRepository;
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
        
        DashboardStatsDTO stats = buildStats(classIds, courseIds);
        List<ClassScoreDistributionDTO> scoreDistributions = buildScoreDistributions(classIds, courseIds);
        
        // 高频错题排行 - 从作业和考试的知识点得分率计算
        List<WrongQuestionDTO> topWrongQuestions = buildTopWrongQuestions(classIds, courseIds);
        
        // 薄弱知识点
        List<WeakKnowledgePointDTO> weakKnowledgePoints = buildWeakKnowledgePoints(classIds, courseIds);
        
        // 活跃度监控
        ActivityMonitorDTO activityMonitor = buildActivityMonitor(classIds, courseIds);
        
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
        List<Student> students = getStudentsByClasses(classIds);
        long studentCount = students.size();
        long classCount = classIds.size();
        long courseCount = courseIds.size();
        
        // 待批改作业数
        long pendingHomeworkCount = 0;
        for (Long courseId : courseIds) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course != null) {
                pendingHomeworkCount += homeworkRepository.findByCourseAndStatus(course, HomeworkStatus.PENDING).size();
            }
        }
        // 即将开始的考试数
        long upcomingExamCount = 0;
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo != null) {
                List<Exam> upcomingExams = examRepository.findByExamDateAfterAndStatus(LocalDate.now().atStartOfDay(), ExamStatus.UPCOMING);
                upcomingExamCount += upcomingExams.stream().filter(e -> e.getClassInfo() != null && e.getClassInfo().getId().equals(classId)).count();
            }
        }
        
        // 低活跃度预警数（从 activity_record 计算）
        long lowActivityAlertCount = countLowActivityStudents(students);
        
        // 薄弱知识点数
        long weakPointCount = 0;
        for (Student student : students) {
            weakPointCount += masteryRepository.findByStudentAndMasteryLevelLessThan(student, 60.0).size();
        }
        
        // 整体平均分和及格率
        double totalScore = 0;
        int scoreCount = 0;
        int passCount = 0;
        for (Long courseId : courseIds) {
            List<ExamGrade> grades = examGradeRepository.findByCourseId(courseId);
            for (ExamGrade grade : grades) {
                if (grade.getScore() != null) {
                    totalScore += grade.getScore();
                    scoreCount++;
                    if (grade.getScore() >= 60) passCount++;
                }
         }
        }
        
        double overallAvgScore = scoreCount > 0 ? totalScore / scoreCount : 0;
        double overallPassRate = scoreCount > 0 ? (passCount * 100.0 / scoreCount) : 0;
        return DashboardStatsDTO.builder()
            .studentCount(studentCount)
            .teacherCount(0L)
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

    private long countLowActivityStudents(List<Student> students) {
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        long count = 0;
        for (Student student : students) {
            List<ActivityRecord> records = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), LocalDate.now().atStartOfDay());
            double score = records.stream().mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0).sum();
            if (score < 20) count++;
        }
        return count;
    }

    /**
     * 构建成绩分布
     */
    private List<ClassScoreDistributionDTO> buildScoreDistributions(List<Long> classIds, List<Long> courseIds) {
        List<ClassScoreDistributionDTO> result = new ArrayList<>();
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo == null) continue;
            
            List<Student> students = studentRepository.findByClassInfo(classInfo);
            List<Double> allScores = new ArrayList<>();
            for (Long courseId : courseIds) {
                List<ExamGrade> grades = examGradeRepository.findByClassIdAndCourseId(classId, courseId);
                for (ExamGrade grade : grades) {
                    if (grade.getScore() != null) allScores.add(grade.getScore().doubleValue());
                }
            }

            if (allScores.isEmpty()) continue;
            
             Map<String, Integer> distribution = new LinkedHashMap<>();
            distribution.put("优秀(>=90)", 0);
            distribution.put("良好(80-89)", 0);
            distribution.put("中等(70-79)", 0);
            distribution.put("及格(60-69)", 0);
            distribution.put("不及格(<60)", 0);
            
            double totalScore = 0, highest = 0, lowest = 100;
            int passCount = 0, excellentCount = 0;
            
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
            
           double avg = totalScore / allScores.size();
            double variance = allScores.stream().mapToDouble(s -> Math.pow(s - avg, 2)).average().orElse(0);
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
        Map<String, WrongQuestionAggregate> aggregateMap = new HashMap<>();
        
        for (Long courseId : courseIds) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) continue;
            
            List<Homework> homeworks = homeworkRepository.findByCourse(course);
            for (Homework homework : homeworks) {
                List<Long> kpIds = homework.getKnowledgePointIds();
                if (kpIds == null || kpIds.isEmpty()) continue;
            
            // 获取该作业的所有提交
                List<Submission> submissions = submissionRepository.findGradedByHomeworkId(homework.getId());
                if (submissions.isEmpty()) continue;
                
                for (Long kpId : kpIds) {
                    String key = courseId + "_" + kpId;
                    WrongQuestionAggregate agg = aggregateMap.computeIfAbsent(key, k -> new WrongQuestionAggregate());
                    agg.knowledgePointId = kpId;
                    agg.courseId = courseId;
                    agg.courseName = course.getName();
                    
                    // 统计得分<60的学生数作为错误人数
                    int errorCount = 0;
                    for (Submission sub : submissions) {
                        if (sub.getScore() != null && sub.getScore() < 60) {
                            errorCount++;
                        }
                    }
                    agg.errorCount += errorCount;
                    agg.totalStudents += submissions.size();
                }}
        }
        
        List<WrongQuestionDTO> result = new ArrayList<>();
        for (WrongQuestionAggregate agg : aggregateMap.values()) {
            double errorRate = agg.totalStudents > 0 ? agg.errorCount * 100.0 / agg.totalStudents : 0;
            result.add(WrongQuestionDTO.builder()
                .knowledgePointId(agg.knowledgePointId)
                .knowledgePointName(getKnowledgePointName(agg.knowledgePointId))
                .courseId(agg.courseId)
                .courseName(agg.courseName)
                .errorCount(agg.errorCount)
                .totalStudents(agg.totalStudents)
                .errorRate(BigDecimal.valueOf(Math.round(errorRate * 100) / 100.0))
                .build());
        }
        
        result.sort((a, b) -> b.getErrorRate().compareTo(a.getErrorRate()));
        return result.stream().limit(10).collect(Collectors.toList());
    }

     private String getKnowledgePointName(Long kpId) {
        // 简化实现，实际可从缓存获取
        return "知识点" + kpId;
    }
    
    private static class WrongQuestionAggregate {
        Long knowledgePointId;
        Long courseId;
        String courseName;
        int errorCount = 0;
        int totalStudents = 0;
    }

    /**
     * 构建薄弱知识点
     */
    private List<WeakKnowledgePointDTO> buildWeakKnowledgePoints(List<Long> classIds, List<Long> courseIds) {
        List<WeakKnowledgePointDTO> result = new ArrayList<>();
        
        for (Long courseId : courseIds) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) continue;
            
            Map<Long, WeakPointAggregate> aggregateMap = new HashMap<>();
            
            for (Long classId : classIds) {
                List<StudentKnowledgeMastery> masteries = masteryRepository.findByClassIdAndCourseId(classId, courseId);
                for (StudentKnowledgeMastery mastery : masteries) {
                    Long kpId = mastery.getKnowledgePoint().getId();
                    WeakPointAggregate agg = aggregateMap.computeIfAbsent(kpId, k -> new WeakPointAggregate());
                    agg.knowledgePointId = kpId;
                    agg.knowledgePointName = mastery.getKnowledgePoint().getName();
                    agg.scores.add(mastery.getMasteryLevel());
                    
                   if (mastery.getMasteryLevel() < 60) {
                        agg.weakStudents.add(WeakStudentDTO.builder()
                            .studentId(mastery.getStudent().getId())
                            .studentName(mastery.getStudent().getUser().getName())
                            .studentNo(mastery.getStudent().getStudentNo())
                            .masteryLevel(mastery.getMasteryLevel())
                            .build());
                    }
                }
            }
            
           for (WeakPointAggregate agg : aggregateMap.values()) {
                double avgMastery = agg.scores.stream().mapToDouble(Double::doubleValue).average().orElse(100);
                if (avgMastery < 70) {
                    result.add(WeakKnowledgePointDTO.builder()
                        .knowledgePointId(agg.knowledgePointId)
                        .knowledgePointName(agg.knowledgePointName)
                        .courseId(courseId)
                        .courseName(course.getName())
                        .avgMastery(Math.round(avgMastery * 100) / 100.0)
                        .studentCount(agg.scores.size())
                        .affectedRate(agg.scores.isEmpty() ? 0 : Math.round((agg.weakStudents.size() * 100.0 / agg.scores.size()) * 100) / 100.0)
                        .weakStudents(agg.weakStudents.stream().limit(5).collect(Collectors.toList()))
                        .build());
                }
            }
        }
        
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
        
        List<Student> students = getStudentsByClasses(classIds);
        
        double thisWeekTotal = 0, lastWeekTotal = 0;
        int thisWeekCount = 0, lastWeekCount = 0;
        List<LowActivityStudentDTO> lowActivityStudents = new ArrayList<>();
        long lowActivityCount = 0;
        
        for (Student student : students) {
            List<ActivityRecord> thisWeekRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), today.atStartOfDay());
            double thisWeekScore = thisWeekRecords.stream().mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0).sum();
            thisWeekTotal += thisWeekScore;
            thisWeekCount++;
            
           List<ActivityRecord> lastWeekRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, twoWeeksAgo.atStartOfDay(), weekAgo.atStartOfDay());
            double lastWeekScore = lastWeekRecords.stream().mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0).sum();
            lastWeekTotal += lastWeekScore;
            lastWeekCount++;
            
            if (thisWeekScore < 20) {
                lowActivityCount++;
                Integer studyDuration = activityRecordService.getStudentStudyDuration(student.getId(), weekAgo);
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
        
        // 严重预警（从 activity_record 计算）
        List<CriticalAlertDTO> criticalAlerts = new ArrayList<>();
        long criticalAlertCount = 0;
        for (Student student : students) {
            List<ActivityRecord> records = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), today.atStartOfDay());
            double score = records.stream().mapToDouble(r -> r.getActivityScore() != null ? r.getActivityScore().doubleValue() : 0).sum();
            if (score < 10) {
                criticalAlertCount++;
                criticalAlerts.add(CriticalAlertDTO.builder()
                    .studentId(student.getId())
                    .studentName(student.getUser().getName())
                    .studentNo(student.getStudentNo())
                    .alertType("LOW_ACTIVITY")
                    .alertLevel("CRITICAL")
                    .activityScore(BigDecimal.valueOf(score))
                    .threshold(BigDecimal.valueOf(10.0))
                    .build());
            }
        } 
       long activeStudentCount = 0;
        for (Student student : students) {
            List<ActivityRecord> records = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), today.atStartOfDay());
            if (!records.isEmpty()) activeStudentCount++;
        }
        
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
            if (classInfo != null) students.addAll(studentRepository.findByClassInfo(classInfo));
        }
        return students;
    }

    private static class WeakPointAggregate {
        Long knowledgePointId;
        String knowledgePointName;
        List<Double> scores = new ArrayList<>();
        List<WeakStudentDTO> weakStudents = new ArrayList<>();
    }
}
