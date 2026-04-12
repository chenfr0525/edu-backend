package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.stereotype.Service;

import com.edu.domain.Homework;
import com.edu.domain.Student;
import com.edu.domain.Submission;
import com.edu.repository.SubmissionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {
   private final SubmissionRepository submissionRepository;
    
    public List<Submission> findAll() {
        return submissionRepository.findAll();
    }
    
    public Submission findById(Long id) {
        return submissionRepository.findById(id).orElse(null);
    }
    
    public List<Submission> findByStudent(Student student) {
        return submissionRepository.findByStudent(student);
    }
    
    public List<Submission> findByHomework(Homework homework) {
        return submissionRepository.findByHomework(homework);
    }
    
    public Submission findByHomeworkAndStudent(Homework homework, Student student) {
        return submissionRepository.findByHomeworkAndStudent(homework, student);
    }
    
    public Submission save(Submission submission) {
        return submissionRepository.save(submission);
    }
    
    public Submission update(Submission submission) {
        return submissionRepository.save(submission);
    }
    
    public void deleteById(Long id) {
        submissionRepository.deleteById(id);
    }
    
    public long countCompletedByStudent(Long studentId) {
        return submissionRepository.countCompletedByStudent(studentId);
    }
    
    public Double getStudentAverageScore(Long studentId) {
        return submissionRepository.getAvgScoreByStudent(studentId);
    }

     public long countLateByStudent(Student student) {
        return submissionRepository.countLateByStudent(student);
    }
}
