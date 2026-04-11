package com.edu.repository;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StudentKnowledgeMasteryRepository extends JpaRepository<StudentKnowledgeMastery, Long> {
  Optional<StudentKnowledgeMastery> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint);
}
