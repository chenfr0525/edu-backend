package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.edu.domain.Course;
import com.edu.domain.KnowledgePoint;
import com.edu.repository.KnowledgePointRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgePointService {
    private final KnowledgePointRepository knowledgePointRepository;
    
    public List<KnowledgePoint> findAll() {
        return knowledgePointRepository.findAll();
    }
    
    public Optional<KnowledgePoint> findById(Long id) {
        return knowledgePointRepository.findById(id);
    }
    
    public List<KnowledgePoint> findByCourse(Course course) {
        return knowledgePointRepository.findByCourse(course);
    }
    
    public List<KnowledgePoint> findByParentId(Long parentId) {
        return knowledgePointRepository.findByParentId(parentId);
    }
    
    public List<KnowledgePoint> findRootPoints() {
        return knowledgePointRepository.findByLevel(0);
    }
    
    public List<KnowledgePoint> findByCourseOrdered(Course course) {
        return knowledgePointRepository.findByCourseOrderBySortOrderAsc(course);
    }
    
    public KnowledgePoint save(KnowledgePoint knowledgePoint) {
        return knowledgePointRepository.save(knowledgePoint);
    }
    
    public KnowledgePoint update(KnowledgePoint knowledgePoint) {
        return knowledgePointRepository.save(knowledgePoint);
    }
    
    public void deleteById(Long id) {
        knowledgePointRepository.deleteById(id);
    }
}
