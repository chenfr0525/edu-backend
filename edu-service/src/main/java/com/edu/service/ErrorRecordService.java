package com.edu.service;

import com.edu.domain.ErrorRecord;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Student;
import com.edu.repository.ErrorRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorRecordService {
   private final ErrorRecordRepository errorRecordRepository;
    
    public List<ErrorRecord> findAll() {
        return errorRecordRepository.findAll();
    }
    
    public ErrorRecord findById(Long id) {
        return errorRecordRepository.findById(id).orElse(null);
    }
    
    public List<ErrorRecord> findByStudent(Student student) {
        return errorRecordRepository.findByStudent(student);
    }
    
    public List<ErrorRecord> findByStudentAndKnowledgePoint(Student student, KnowledgePoint kp) {
        return errorRecordRepository.findByStudentAndKnowledgePoint(student, kp);
    }
    
    public ErrorRecord save(ErrorRecord errorRecord) {
        return errorRecordRepository.save(errorRecord);
    }
    
    public ErrorRecord update(ErrorRecord errorRecord) {
        return errorRecordRepository.save(errorRecord);
    }
    
    public void deleteById(Long id) {
        errorRecordRepository.deleteById(id);
    }
    
    public List<Object[]> findClassHighFrequencyErrors(Long classId) {
        return errorRecordRepository.findHighFrequencyErrorsByClass(classId);
    }
}
