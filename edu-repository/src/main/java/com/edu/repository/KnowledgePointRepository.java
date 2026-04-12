package com.edu.repository;

import com.edu.domain.Course;
import com.edu.domain.KnowledgePoint;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
  List<KnowledgePoint> findByCourse(Course course);    
    // 根据父知识点查询子知识点
    List<KnowledgePoint> findByParentId(Long parentId);
    
    // 查询根知识点（一级）
    List<KnowledgePoint> findByLevel(int level);

    Optional<KnowledgePoint> findById(Long id);
    
    // 按排序查询
    List<KnowledgePoint> findByCourseOrderBySortOrderAsc(Course course);
}
