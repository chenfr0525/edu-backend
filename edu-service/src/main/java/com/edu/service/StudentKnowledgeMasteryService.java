package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.repository.StudentKnowledgeMasteryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentKnowledgeMasteryService {
   private final StudentKnowledgeMasteryRepository masteryRepository;
    
    public List<StudentKnowledgeMastery> findAll() {
        return masteryRepository.findAll();
    }
    
    public Optional<StudentKnowledgeMastery> findById(Long id) {
        return masteryRepository.findById(id);
    }
    
    public List<StudentKnowledgeMastery> findByStudent(Student student) {
        return masteryRepository.findByStudent(student);
    }
    
    public Optional<StudentKnowledgeMastery> findByStudentAndKnowledgePoint(Student student, KnowledgePoint kp) {
        return masteryRepository.findByStudentAndKnowledgePoint(student, kp);
    }
    
    public List<StudentKnowledgeMastery> findWeakPoints(Student student, Double threshold) {
        return masteryRepository.findByStudentAndMasteryLevelLessThan(student, threshold);
    }
    
    public List<StudentKnowledgeMastery> findStrongPoints(Student student, Double threshold) {
        return masteryRepository.findByStudentAndMasteryLevelGreaterThan(student, threshold);
    }
    
    public StudentKnowledgeMastery save(StudentKnowledgeMastery mastery) {
        return masteryRepository.save(mastery);
    }
    
    public StudentKnowledgeMastery update(StudentKnowledgeMastery mastery) {
        return masteryRepository.save(mastery);
    }
    
    public void deleteById(Long id) {
        masteryRepository.deleteById(id);
    }
    
    // public Double getClassAverageMastery(Long classId, Long kpId) {
    //     return masteryRepository.getClassAvgMastery(classId, kpId);
    // }
}
