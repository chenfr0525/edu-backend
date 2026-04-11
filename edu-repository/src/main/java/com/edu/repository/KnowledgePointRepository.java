package com.edu.repository;

import com.edu.domain.Course;
import com.edu.domain.KnowledgePoint;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
  List<KnowledgePoint> findByCourse(Course course);
}
