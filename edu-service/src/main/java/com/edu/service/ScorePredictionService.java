package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Service;

import com.edu.domain.Course;
import com.edu.domain.ScorePrediction;
import com.edu.domain.Student;
import com.edu.repository.ScorePredictionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScorePredictionService {
  private final ScorePredictionRepository predictionRepository;
    
    public List<ScorePrediction> findAll() {
        return predictionRepository.findAll();
    }
    
    public ScorePrediction findById(Long id) {
        return predictionRepository.findById(id).orElse(null);
    }
    
    public ScorePrediction findLatestByStudentAndCourse(Student student, Course course) {
        return predictionRepository.findFirstByStudentAndCourseOrderByPredictionDateDesc(student, course);
    }
    
    public List<ScorePrediction> findByStudent(Student student) {
        return predictionRepository.findByStudent(student);
    }
    
    public List<ScorePrediction> findPendingVerification() {
        return predictionRepository.findByActualScoreIsNotNull();
    }
    
    public ScorePrediction save(ScorePrediction prediction) {
        return predictionRepository.save(prediction);
    }
    
    public ScorePrediction update(ScorePrediction prediction) {
        return predictionRepository.save(prediction);
    }
    
    public void deleteById(Long id) {
        predictionRepository.deleteById(id);
    }
}
