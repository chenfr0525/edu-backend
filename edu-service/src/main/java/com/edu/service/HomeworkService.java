package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.edu.domain.Course;
import com.edu.domain.Homework;
import com.edu.domain.HomeworkStatus;
import com.edu.repository.HomeworkRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkService {
   private final HomeworkRepository homeworkRepository;
    
    public List<Homework> findAll() {
        return homeworkRepository.findAll();
    }
    
    public Optional<Homework> findById(Long id) {
        return homeworkRepository.findById(id);
    }
    
    public List<Homework> findByCourse(Course course) {
        return homeworkRepository.findByCourse(course);
    }
    
    public List<Homework> findByStatus(HomeworkStatus status) {
        return homeworkRepository.findByStatus(status);
    }
    
    public List<Homework> findDeadlineSoon() {
        return homeworkRepository.findByDeadlineAfterAndStatus(LocalDateTime.now(), "ONGOING");
    }
    
    public Homework save(Homework homework) {
        return homeworkRepository.save(homework);
    }
    
    public Homework update(Homework homework) {
        return homeworkRepository.save(homework);
    }
    
    public void deleteById(Long id) {
        homeworkRepository.deleteById(id);
    }
    
    public Double getCourseAverageScore(Long courseId) {
        return homeworkRepository.getAvgScoreByCourse(courseId);
    }
}
