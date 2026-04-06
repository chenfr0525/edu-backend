package com.edu.service;

import com.edu.domain.*;
import com.edu.domain.dto.GradeTrendDTO;
import com.edu.domain.dto.KnowledgeMasteryDTO;
import com.edu.domain.dto.StudentStatsDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final GradeRepository gradeRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SemesterRepository semesterRepository;
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;

    public StudentStatsDTO getStudentStats(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        List<Enrollment> enrollments = enrollmentRepository.findByStudent(student);
        List<ActivityRecord> activities = activityRecordRepository.findByUser(student);
        List<Grade> grades = gradeRepository.findByStudent(student);

        double avgScore = grades.stream().mapToDouble(Grade::getScore).average().orElse(0.0);
        int studyHours = activities.stream().mapToInt(ActivityRecord::getDuration).sum() / 60;
        
        // Mock rank and total students for now
        int classRank = 14; 
        int totalStudents = 45;

        return StudentStatsDTO.builder()
                .courseCount(enrollments.size())
                .studyHours(studyHours)
                .avgScore(avgScore)
                .classRank(classRank)
                .totalStudents(totalStudents)
                .build();
    }

    public List<Semester> getSemesters() {
        return semesterRepository.findAll();
    }

    public List<Enrollment> getEnrollments(String username, String semesterCode) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (semesterCode != null) {
            Semester semester = semesterRepository.findByCode(semesterCode)
                    .orElseThrow(() -> new RuntimeException("Semester not found"));
            return enrollmentRepository.findByStudentAndSemester(student, semester);
        }
        return enrollmentRepository.findByStudent(student);
    }

    public KnowledgeMasteryDTO getKnowledgeMastery(String username, Long courseId) {
        // This would typically involve complex aggregation of question performance
        // For now, returning mock-like data structured correctly
        return KnowledgeMasteryDTO.builder()
                .current(Arrays.asList(85, 70, 90, 65, 80, 75))
                .classAvg(Arrays.asList(75, 65, 80, 70, 75, 65))
                .indicators(Arrays.asList("Vue3 基础", "Pinia 状态管理", "Vue Router 路由", "ECharts 可视化", "Element Plus 组件", "项目实战能力"))
                .build();
    }

    public GradeTrendDTO getGradeTrend(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        List<Grade> grades = gradeRepository.findByStudent(student);
        
        List<String> exams = grades.stream().map(g -> g.getExam().getName()).collect(Collectors.toList());
        List<Double> scores = grades.stream().map(Grade::getScore).collect(Collectors.toList());
        // Mock class average
        List<Double> classAvg = grades.stream().map(g -> g.getScore() - 5.0).collect(Collectors.toList());

        return GradeTrendDTO.builder()
                .exams(exams)
                .scores(scores)
                .classAvg(classAvg)
                .build();
    }

    public List<Object[]> getAttendanceHeatmap(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        LocalDate now = LocalDate.now();
        List<Attendance> attendances = attendanceRepository.findByStudentAndDateBetween(student, now.minusYears(1), now);
        
        return attendances.stream()
                .map(a -> new Object[]{a.getDate().toString(), a.getAttendanceRate()})
                .collect(Collectors.toList());
    }

    public List<Homework> getHomeworks(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        List<Enrollment> enrollments = enrollmentRepository.findByStudent(student);
        List<Course> courses = enrollments.stream().map(Enrollment::getCourse).collect(Collectors.toList());
        
        return homeworkRepository.findByCourseIn(courses);
    }

    public Submission submitHomework(String username, Long homeworkId, String content, String files) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        
        Submission submission = submissionRepository.findByStudentAndHomework(student, homework)
                .orElse(new Submission());
        
        submission.setStudent(student);
        submission.setHomework(homework);
        submission.setContent(content);
        submission.setFiles(files);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        
        return submissionRepository.save(submission);
    }
}
