package com.edu.repository;

import com.edu.domain.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
}
