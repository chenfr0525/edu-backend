package com.edu.service;

import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentManageService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ExamGradeRepository examGradeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
     * 获取教师可见的课程列表
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return new ArrayList<>();
        }
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
     * 获取学生列表（支持班级、课程筛选和模糊查询）
     */
    @Transactional(readOnly = true)
    public Page<Student> getStudentList(StudentListRequest request, 
                                         Long currentUserId, 
                                         String userRole) {
        // 1. 确定可见的班级ID列表
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, request.getClassId());
        
        if (visibleClassIds.isEmpty()) {
            return Page.empty();
        }
        
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        // 2. 如果有课程筛选，优先按课程查询
        if (request.getCourseId() != null) {
            return getStudentsByCourse(request.getCourseId(), request.getKeyword(), pageable, visibleClassIds);
        }
        
        // 3. 按班级筛选
        return getStudentsByClasses(visibleClassIds, request.getKeyword(), pageable);
    }

    /**
     * 获取可见的班级ID列表
     */
    private List<Long> getVisibleClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) {
                return Arrays.asList(requestClassId);
            }
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
     * 按课程查询学生
     */
    private Page<Student> getStudentsByCourse(Long courseId, String keyword, 
                                               Pageable pageable, List<Long> visibleClassIds) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return Page.empty();
        }
        
        // 获取选修该课程的学生，并过滤班级权限
        Page<Student> studentPage;
        if (keyword != null && !keyword.isEmpty()) {
            studentPage = studentRepository.findByCourseIdAndKeyword(courseId, keyword, pageable);
        } else {
            studentPage = studentRepository.findByCourseId(courseId, pageable);
        }
        
        // 过滤班级权限
        List<Student> filtered = studentPage.getContent().stream()
            .filter(s -> visibleClassIds.contains(s.getClassInfo().getId()))
            .collect(Collectors.toList());
        
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    /**
     * 按班级查询学生
     */
    private Page<Student> getStudentsByClasses(List<Long> classIds, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return studentRepository.findByClassIdsAndKeyword(classIds, keyword, pageable);
        } else {
            return studentRepository.findByClassIds(classIds, pageable);
        }
    }

    /**
     * 获取学生管理统计卡片数据
     */
    @Transactional(readOnly = true)
    public StudentManageStatsVO getStats(Long currentUserId, String userRole, Long classId, Long courseId) {
        // 获取可见的学生列表
        List<Student> students = getVisibleStudents(currentUserId, userRole, classId, courseId);
        
        if (students.isEmpty()) {
            return StudentManageStatsVO.builder()
                .totalStudentCount(0L)
                .activeStudentCount(0L)
                .lowActivityCount(0L)
                .weakPointStudentCount(0L)
                .avgActivityScore(0.0)
                .avgExamScore(0.0)
                .maleCount(0L)
                .femaleCount(0L)
                .build();
        }
        
        Set<Long> studentIds = students.stream()
            .map(Student::getId)
            .collect(Collectors.toSet());
        
        // 统计活跃学生数（近7天有活动）
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        long activeCount = 0;
        double totalActivityScore = 0;
        
        // 统计有薄弱知识点的学生数
        long weakPointCount = 0;
        
        // 统计成绩
        double totalExamScore = 0;
        int examScoreCount = 0;
        
        // 统计性别
        long maleCount = 0;
        long femaleCount = 0;
        
        for (Student student : students) {
            // 性别统计
            User user = student.getUser();
            if ("男".equals(user.getGender())) {
                maleCount++;
            } else if ("女".equals(user.getGender())) {
                femaleCount++;
            }
            
            // 活跃度统计
            List<ActivityRecord> recentActivities = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), LocalDate.now().atStartOfDay());
            if (!recentActivities.isEmpty()) {
                activeCount++;
            }
            
            Double activityScore = getStudentActivityScore(student.getId());
            if (activityScore != null) {
                totalActivityScore += activityScore;
            }
            
            // 薄弱知识点统计
            List<StudentKnowledgeMastery> weakPoints = masteryRepository
                .findByStudentAndMasteryLevelLessThan(student, Double.valueOf(60));
            if (!weakPoints.isEmpty()) {
                weakPointCount++;
            }
            
            // 成绩统计
            Double avgScore = examGradeRepository.getStudentAvgScore(student.getId());
            if (avgScore != null) {
                totalExamScore += avgScore;
                examScoreCount++;
            }
        }
        
        // 低活跃度统计（活跃度<20）
        long lowActivityCount = students.stream()
            .filter(s -> {
                Double score = getStudentActivityScore(s.getId());
                return score != null && score < 20;
            })
            .count();
        
        return StudentManageStatsVO.builder()
            .totalStudentCount((long) students.size())
            .activeStudentCount(activeCount)
            .lowActivityCount(lowActivityCount)
            .weakPointStudentCount(weakPointCount)
            .avgActivityScore(students.isEmpty() ? 0 : Math.round((totalActivityScore / students.size()) * 100) / 100.0)
            .avgExamScore(examScoreCount == 0 ? 0 : Math.round((totalExamScore / examScoreCount) * 100) / 100.0)
            .maleCount(maleCount)
            .femaleCount(femaleCount)
            .build();
    }

    /**
     * 获取可见的学生列表
     */
    private List<Student> getVisibleStudents(Long userId, String userRole, Long classId, Long courseId) {
        List<Long> classIds = getVisibleClassIds(userId, userRole, classId);
        
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Student> students = studentRepository.findAllByClassIds(classIds);
        
        // 如果指定了课程，过滤选修该课程的学生
        if (courseId != null) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course != null) {
                Set<Long> enrolledStudentIds = enrollmentRepository.findByCourse(course).stream()
                    .map(e -> e.getStudent().getId())
                    .collect(Collectors.toSet());
                students = students.stream()
                    .filter(s -> enrolledStudentIds.contains(s.getId()))
                    .collect(Collectors.toList());
            }
        }
        
        return students;
    }

    /**
     * 获取学生活跃度得分
     */
    private Double getStudentActivityScore(Long studentId) {
        return activityRecordRepository.getTotalActivityScore(studentId);
    }  
 
}