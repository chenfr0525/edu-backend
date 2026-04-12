package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.Student;
import com.edu.repository.ExamGradeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradeService {
   private final ExamGradeRepository examGradeRepository;
    
    public List<ExamGrade> findAll() {
        return examGradeRepository.findAll();
    }
    
    public Optional<ExamGrade> findById(Long id) {
        return examGradeRepository.findById(id);
    }
    
    public List<ExamGrade> findByStudent(Student student) {
        return examGradeRepository.findByStudent(student);
    }
    
    public List<ExamGrade> findByExam(Exam exam) {
        return examGradeRepository.findByExam(exam);
    }
    
    public List<ExamGrade> findByExamAndStudent(Exam exam,Student student) {
        return examGradeRepository.findByExamAndStudent(exam, student);
    }
    
    public List<ExamGrade> getStudentScoreTrend(Student student) {
        return examGradeRepository.findByStudentOrderByCreatedAtAsc(student);
    }
    
    public List<ExamGrade> getClassRanking(Exam exam) {
        return examGradeRepository.findByExamOrderByScoreDesc(exam);
    }
    
    public ExamGrade save(ExamGrade examGrade) {
        return examGradeRepository.save(examGrade);
    }
    
    public ExamGrade update(ExamGrade examGrade) {
        return examGradeRepository.save(examGrade);
    }
    
    public void deleteById(Long id) {
        examGradeRepository.deleteById(id);
    }
    
    public Double getStudentAverageScore(Long studentId) {
        return examGradeRepository.getStudentAvgScore(studentId);
    }
       // 获取最新排名
    public Integer getLatestRank(Long studentId) {
        List<Integer> ranks = examGradeRepository.getLatestRank(studentId);
        return ranks.isEmpty() ? null : ranks.get(0);
    }
    
    // 获取最新趋势
    public String getLatestTrend(Long studentId) {
        List<String> trends = examGradeRepository.getLatestTrend(studentId);
        return trends.isEmpty() ? null : trends.get(0);
    }
    
    // 使用 Pageable 的方式
    public Integer getLatestRankWithPageable(Long studentId) {
        Pageable pageable = PageRequest.of(0, 1);
        List<Integer> ranks = examGradeRepository.getLatestRank(studentId, pageable);
        return ranks.isEmpty() ? null : ranks.get(0);
    }
}
