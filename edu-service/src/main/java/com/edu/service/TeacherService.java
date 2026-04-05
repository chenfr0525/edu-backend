package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final ClassRepository classRepository;
    private final ExamRepository examRepository;
    private final GradeRepository gradeRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final UserRepository userRepository;

    public List<ClassInfo> getClassesByTeacher(String username) {
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        return classRepository.findByTeacher(teacher);
    }

    public List<Exam> getExamsByClass(Long classId) {
        ClassInfo classInfo = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        return examRepository.findByClassInfo(classInfo);
    }

    public Map<String, Object> getGradeDistribution(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        List<Grade> grades = gradeRepository.findByExam(exam);

        // Group by score ranges: Excellent (>=90), Good (75-89), Average (60-74), Poor (<60)
        long excellent = grades.stream().filter(g -> g.getScore() >= 90).count();
        long good = grades.stream().filter(g -> g.getScore() >= 75 && g.getScore() < 90).count();
        long average = grades.stream().filter(g -> g.getScore() >= 60 && g.getScore() < 75).count();
        long poor = grades.stream().filter(g -> g.getScore() < 60).count();

        Map<String, Object> result = new HashMap<>();
        result.put("categories", java.util.Arrays.asList("Excellent(>=90)", "Good(75-89)", "Average(60-74)", "Poor(<60)"));
        result.put("data", java.util.Arrays.asList(excellent, good, average, poor));
        result.put("colors", java.util.Arrays.asList("#67c23a", "#409eff", "#e6a23c", "#f56c6c"));
        return result;
    }

    public Map<String, Object> getHighFrequencyErrors(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        List<ErrorRecord> errors = errorRecordRepository.findByExam(exam);

        // Group errors by question
        Map<Question, Long> errorCounts = errors.stream()
                .collect(Collectors.groupingBy(ErrorRecord::getQuestion, Collectors.counting()));

        // Sort and get top errors
        List<Map.Entry<Question, Long>> sortedErrors = errorCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        List<String> questions = sortedErrors.stream().map(e -> "Question " + e.getKey().getId()).collect(Collectors.toList());
        List<Long> errorRates = sortedErrors.stream().map(e -> e.getValue()).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("questions", questions);
        result.put("errorRates", errorRates);
        return result;
    }
}
