package com.edu.service;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Student;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgePointScoreDetailService {
   private final KnowledgePointScoreDetailRepository detailRepository;
    
    public List<KnowledgePointScoreDetail> findAll() {
        return detailRepository.findAll();
    }
    
    public KnowledgePointScoreDetail findById(Long id) {
        return detailRepository.findById(id).orElse(null);
    }
    
    public List<KnowledgePointScoreDetail> findByStudent(Student student) {
        return detailRepository.findByStudent(student);
    }
    
    public List<KnowledgePointScoreDetail> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint) {
        return detailRepository.findByStudentAndKnowledgePoint(student, knowledgePoint);
    }
    
    public KnowledgePointScoreDetail findLatestByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint) {
        return detailRepository.findFirstByStudentAndKnowledgePointOrderByCreatedAtDesc(student, knowledgePoint);
    }
    
    public KnowledgePointScoreDetail save(KnowledgePointScoreDetail detail) {
        return detailRepository.save(detail);
    }
    
    public KnowledgePointScoreDetail update(KnowledgePointScoreDetail detail) {
        return detailRepository.save(detail);
    }
    
    public void deleteById(Long id) {
        detailRepository.deleteById(id);
    }
    
    public BigDecimal getStudentAverageScoreRate(Student student) {
        return detailRepository.getStudentAvgScoreRateByStudent(student);
    }
}
