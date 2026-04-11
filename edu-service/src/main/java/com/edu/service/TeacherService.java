package com.edu.service;

import com.edu.domain.*;
import com.edu.domain.dto.TeacherStatsDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final ClassRepository classRepository;
    private final ExamRepository examRepository;
    private final GradeRepository gradeRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // public List<ClassInfo> getClassesByTeacher(String username) {
    //     User teacher = userRepository.findByUsername(username)
    //             .orElseThrow(() -> new RuntimeException("Teacher not found"));
    //     return classRepository.findByTeacher(teacher);
    // }

    public List<Exam> getExamsByClass(Long classId) {
        ClassInfo classInfo = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        return examRepository.findByClassInfo(classInfo);
    }

    public TeacherStatsDTO getTeacherStats(Long classId) {
        ClassInfo classInfo = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        
        List<Exam> exams = examRepository.findByClassInfo(classInfo);
        List<Grade> allGrades = exams.stream()
                .flatMap(e -> gradeRepository.findByExam(e).stream())
                .collect(Collectors.toList());

        int studentCount = 45; // This should come from a student-class mapping table in a real app
        double avgScore = allGrades.stream().mapToDouble(Grade::getScore).average().orElse(0.0);
        long passCount = allGrades.stream().filter(g -> g.getScore() >= 60).count();
        long excellentCount = allGrades.stream().filter(g -> g.getScore() >= 90).count();

        return TeacherStatsDTO.builder()
                .studentCount(studentCount)
                .avgScore(avgScore)
                .passRate(allGrades.isEmpty() ? 0.0 : (double) passCount / allGrades.size() * 100)
                .excellentRate(allGrades.isEmpty() ? 0.0 : (double) excellentCount / allGrades.size() * 100)
                .pendingHomework(8)
                .lowScoreCount(3)
                .highScoreCount(19)
                .build();
    }

    public Map<String, Object> getGradeDistribution(Long classId, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        List<Grade> grades = gradeRepository.findByExam(exam);

        long excellent = grades.stream().filter(g -> g.getScore() >= 90).count();
        long good = grades.stream().filter(g -> g.getScore() >= 75 && g.getScore() < 90).count();
        long average = grades.stream().filter(g -> g.getScore() >= 60 && g.getScore() < 75).count();
        long poor = grades.stream().filter(g -> g.getScore() < 60).count();

        Map<String, Object> result = new HashMap<>();
        result.put("categories", Arrays.asList("优(≥90)", "良(75-89)", "中(60-74)", "差(<60)"));
        result.put("data", Arrays.asList(excellent, good, average, poor));
        result.put("colors", Arrays.asList("#67c23a", "#409eff", "#e6a23c", "#f56c6c"));
        
        double total = grades.size();
        result.put("percentages", Arrays.asList(
            total == 0 ? 0 : excellent / total * 100,
            total == 0 ? 0 : good / total * 100,
            total == 0 ? 0 : average / total * 100,
            total == 0 ? 0 : poor / total * 100
        ));
        
        return result;
    }

    // public Map<String, Object> getHighFrequencyErrors(Long classId, Long examId) {
    //     Exam exam = examRepository.findById(examId)
    //             .orElseThrow(() -> new RuntimeException("Exam not found"));
    //     List<ErrorRecord> errors = errorRecordRepository.findByExam(exam);

    //     Map<Question, Long> errorCounts = errors.stream()
    //             .filter(e -> e.getQuestion() != null)
    //             .collect(Collectors.groupingBy(ErrorRecord::getQuestion, Collectors.counting()));

    //     List<Map.Entry<Question, Long>> sortedErrors = errorCounts.entrySet().stream()
    //             .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
    //             .limit(5)
    //             .collect(Collectors.toList());

    //     List<String> questions = sortedErrors.stream().map(e -> "第" + e.getKey().getId() + "题").collect(Collectors.toList());
    //     List<Long> errorRates = sortedErrors.stream().map(Map.Entry::getValue).collect(Collectors.toList());
    //     List<Long> ids = sortedErrors.stream().map(e -> e.getKey().getId()).collect(Collectors.toList());

    //     Map<String, Object> result = new HashMap<>();
    //     result.put("questions", questions);
    //     result.put("errorRates", errorRates);
    //     result.put("questionIds", ids);
    //     return result;
    // }

    // Student Management Methods
    public Page<User> getStudents(String keyword, Long classId, UserStatus status, Pageable pageable) {
        // This would require a more complex specification or query in a real app
        // For now, using basic findAll
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User createStudent(User student) {
        student.setRole(Role.STUDENT);
        student.setPassword(passwordEncoder.encode("123456"));
        student.setStatus(UserStatus.PENDING);
        return userRepository.save(student);
    }

    // @Transactional
    // public User updateStudent(Long id, User studentDetails) {
    //     User student = userRepository.findById(id)
    //             .orElseThrow(() -> new RuntimeException("Student not found"));
        
    //     student.setName(studentDetails.getName());
    //     student.setEmail(studentDetails.getEmail());
    //     student.setPhone(studentDetails.getPhone());
    //     student.setStudentNo(studentDetails.getStudentNo());
    //     if (studentDetails.getStatus() != null) {
    //         student.setStatus(studentDetails.getStatus());
    //     }
        
    //     return userRepository.save(student);
    // }

    @Transactional
    public void deleteStudent(Long id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
