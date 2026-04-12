package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;
import com.edu.repository.ExamRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {
   private final ExamRepository examRepository;
    
    public List<Exam> findAll() {
        return examRepository.findAll();
    }
    
    public Optional<Exam> findById(Long id) {
        return examRepository.findById(id);
    }
    
    public List<Exam> findByCourse(Course course) {
        return examRepository.findByCourse(course);
    }
    
    public List<Exam> findByClassInfo(ClassInfo classInfo) {
        return examRepository.findByClassInfo(classInfo);
    }
    
    public List<Exam> findByType(String type) {
        return examRepository.findByType(type);
    }
    
    public List<Exam> findUpcomingExams() {
         LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return examRepository.findByExamDateAfterAndStatus(todayStart, ExamStatus.UPCOMING);
    }
    
    public Exam save(Exam exam) {
        return examRepository.save(exam);
    }
    
    public Exam update(Exam exam) {
        return examRepository.save(exam);
    }
    
    public void deleteById(Long id) {
        examRepository.deleteById(id);
    }
    
    public Double getClassAverageScore(Long classId) {
        return examRepository.getClassAvgScore(classId);
    }
}
